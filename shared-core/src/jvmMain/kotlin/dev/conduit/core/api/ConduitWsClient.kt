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
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
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
        install(WebSockets) {
            pingIntervalMillis = 10_000
        }
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
        connectionJob?.cancel()
        connectionJob = scope.launch {
            val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
            val maskedToken = if (token.length <= 8) token else token.take(4) + "..." + token.takeLast(4)
            log.info("WS connect() starting, url={}, token={}", wsUrl, maskedToken)
            var attempt = 0

            while (isActive) {
                val state = if (attempt == 0) WsConnectionState.CONNECTING else WsConnectionState.RECONNECTING
                _connectionState.value = state
                log.info("WS state -> {} (attempt {})", state, attempt)

                try {
                    client.webSocket("$wsUrl/api/v1/ws?token=$token") {
                        session = this
                        _connectionState.value = WsConnectionState.CONNECTED
                        log.info("WS connected successfully")
                        attempt = 0

                        replaySubscriptions()

                        // Application-level PING validates end-to-end message delivery
                        // (Ktor-level pingIntervalMillis only checks TCP liveness).
                        // Interval is intentionally longer than the transport keepalive
                        // to avoid redundant noise on the SharedFlow.
                        val pingJob = launch {
                            while (isActive) {
                                delay(30_000)
                                try {
                                    val pingMsg = WsMessage(
                                        type = WsMessage.PING,
                                        payload = buildJsonObject {},
                                        timestamp = Clock.System.now(),
                                    )
                                    send(Frame.Text(json.encodeToString(WsMessage.serializer(), pingMsg)))
                                } catch (e: Exception) {
                                    log.warn("WS ping send failed: {}", e.message)
                                }
                            }
                        }

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    try {
                                        val msg = json.decodeFromString<WsMessage>(frame.readText())
                                        _messages.emit(msg)
                                    } catch (e: Exception) {
                                        log.warn("Failed to parse WS message: type={}", e.message)
                                    }
                                }
                                is Frame.Close -> {
                                    val reason = frame.readReason()
                                    log.warn("WS close frame received: code={}, message={}", reason?.code, reason?.message)
                                }
                                else -> {
                                    // PING/PONG handled by Ktor engine
                                }
                            }
                        }

                        log.warn("WS incoming channel closed (server disconnected)")
                        pingJob.cancel()
                    }
                } catch (e: CancellationException) {
                    log.info("WS connection job cancelled")
                    throw e
                } catch (e: Exception) {
                    if (attempt == 0) {
                        log.error("WS connection failed: {}", e.message, e)
                    } else {
                        log.warn("WS reconnect attempt {} failed: {}", attempt, e.message)
                    }
                } finally {
                    session = null
                }

                if (!isActive) {
                    log.info("WS connect loop exiting (scope cancelled)")
                    break
                }

                _connectionState.value = WsConnectionState.DISCONNECTED

                attempt++
                val delayMs = min(1_000L * 2.0.pow(attempt - 1).toLong(), 30_000L)
                log.info("WS reconnecting in {}ms (attempt {})", delayMs, attempt)
                delay(delayMs)
            }

            _connectionState.value = WsConnectionState.DISCONNECTED
            log.info("WS connect() ended (scope done)")
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

        val msg = json.encodeToString(SubscribeRequest(instanceId = instanceId, channels = channelsSet.toList()))
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

        val msg = json.encodeToString(UnsubscribeRequest(instanceId = instanceId, channels = channelsSet.toList()))
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
