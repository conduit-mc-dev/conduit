package dev.conduit.core.api

import dev.conduit.core.model.SubscribeRequest
import dev.conduit.core.model.UnsubscribeRequest
import dev.conduit.core.model.WsConnectionState
import dev.conduit.core.model.WsMessage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable
import kotlin.math.min
import kotlin.math.pow

class ConduitWsClient(
    private val baseUrl: String,
    private val token: String,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : Closeable {

    private val log = LoggerFactory.getLogger(ConduitWsClient::class.java)

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private val _messages = MutableSharedFlow<WsMessage>(extraBufferCapacity = 256)
    val messages: SharedFlow<WsMessage> = _messages

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    // Track pending subscriptions as instanceId -> sorted channel set.
    // LinkedHashMap preserves insertion order for deterministic replay.
    private val _pendingSubscriptions = LinkedHashMap<String, Set<String>>()
    val pendingSubscriptions: Set<Pair<String, Set<String>>>
        get() = _pendingSubscriptions.map { (k, v) -> k to v }.toSet()
    val pendingSubscriptionCount: Int
        get() = _pendingSubscriptions.size

    fun connect(scope: CoroutineScope) {
        connectionJob = scope.launch {
            val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
            var attempt = 0

            while (isActive) {
                _connectionState.value = if (attempt == 0) {
                    WsConnectionState.CONNECTING
                } else {
                    WsConnectionState.RECONNECTING
                }

                try {
                    client.webSocket("$wsUrl/api/v1/ws?token=$token") {
                        session = this
                        _connectionState.value = WsConnectionState.CONNECTED
                        attempt = 0

                        replaySubscriptions()

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                try {
                                    val msg = json.decodeFromString<WsMessage>(frame.readText())
                                    _messages.emit(msg)
                                } catch (e: Exception) {
                                    log.warn("Failed to parse WS message", e)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (attempt == 0) {
                        log.error("WebSocket connection failed", e)
                    } else {
                        log.warn("WebSocket reconnect attempt {} failed: {}", attempt, e.message)
                    }
                } finally {
                    session = null
                }

                if (!isActive) break

                // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s cap
                attempt++
                val delayMs = min(1_000L * 2.0.pow(attempt - 1).toLong(), 30_000L)
                log.info("Reconnecting in {}ms (attempt {})", delayMs, attempt)
                delay(delayMs)
            }

            _connectionState.value = WsConnectionState.DISCONNECTED
        }
    }

    private suspend fun replaySubscriptions() {
        for ((instanceId, channels) in _pendingSubscriptions) {
            val msg = json.encodeToString(SubscribeRequest(
                instanceId = instanceId,
                channels = channels.toList(),
            ))
            try {
                session?.send(Frame.Text(msg))
            } catch (e: Exception) {
                log.warn("Failed to replay subscription for {}: {}", instanceId, e.message)
            }
        }
    }

    suspend fun subscribe(instanceId: String, channels: List<String> = WsMessage.DEFAULT_CHANNELS) {
        val channelsSet = channels.toSortedSet()
        _pendingSubscriptions[instanceId] = channelsSet

        val msg = json.encodeToString(SubscribeRequest(instanceId = instanceId, channels = channels))
        try {
            session?.send(Frame.Text(msg))
        } catch (_: Exception) {
            // Session is null or broken; subscription is queued, will replay on reconnect
        }
    }

    suspend fun unsubscribe(instanceId: String, channels: List<String> = WsMessage.DEFAULT_CHANNELS) {
        val channelsSet = channels.toSortedSet()

        val existing = _pendingSubscriptions[instanceId]
        if (existing != null && existing == channelsSet) {
            _pendingSubscriptions.remove(instanceId)
        }

        val msg = json.encodeToString(UnsubscribeRequest(instanceId = instanceId, channels = channels))
        try {
            session?.send(Frame.Text(msg))
        } catch (_: Exception) {
            // Session is null or broken; removal from pending above is sufficient
        }
    }

    override fun close() {
        connectionJob?.cancel()
        client.close()
        _connectionState.value = WsConnectionState.DISCONNECTED
    }
}
