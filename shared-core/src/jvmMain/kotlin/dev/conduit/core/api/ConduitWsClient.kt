package dev.conduit.core.api

import dev.conduit.core.model.SubscribeRequest
import dev.conduit.core.model.UnsubscribeRequest
import dev.conduit.core.model.WsMessage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.Closeable

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

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    fun connect(scope: CoroutineScope) {
        connectionJob = scope.launch {
            val wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://")
            try {
                client.webSocket("$wsUrl/api/v1/ws?token=$token") {
                    session = this
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
                log.error("WebSocket connection error", e)
            } finally {
                session = null
            }
        }
    }

    suspend fun subscribe(instanceId: String, channels: List<String> = WsMessage.DEFAULT_CHANNELS) {
        val msg = json.encodeToString(SubscribeRequest(instanceId = instanceId, channels = channels))
        session?.send(Frame.Text(msg))
    }

    suspend fun unsubscribe(instanceId: String, channels: List<String> = WsMessage.DEFAULT_CHANNELS) {
        val msg = json.encodeToString(UnsubscribeRequest(instanceId = instanceId, channels = channels))
        session?.send(Frame.Text(msg))
    }

    override fun close() {
        connectionJob?.cancel()
        client.close()
    }
}
