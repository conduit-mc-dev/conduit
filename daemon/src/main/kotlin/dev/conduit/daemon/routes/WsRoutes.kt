package dev.conduit.daemon.routes

import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.ServerProcessManager
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.store.TokenStore
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("dev.conduit.daemon.routes.WsRoutes")

fun Route.wsRoutes(
    broadcaster: WsBroadcaster,
    tokenStore: TokenStore,
    processManager: ServerProcessManager,
    json: Json,
) {
    webSocket("/api/v1/ws") {
        val token = call.request.queryParameters["token"]
        if (token == null || tokenStore.validateToken(token) == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return@webSocket
        }

        broadcaster.addSession(this)
        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    try {
                        val text = frame.readText()
                        val msg = json.parseToJsonElement(text).jsonObject
                        val type = msg["type"]?.jsonPrimitive?.content ?: continue

                        when (type) {
                            WsMessage.SUBSCRIBE, WsMessage.UNSUBSCRIBE -> {
                                val instanceId = msg["instanceId"]?.jsonPrimitive?.content ?: continue
                                val channels = msg["channels"]?.jsonArray
                                    ?.map { it.jsonPrimitive.content }
                                    ?: WsMessage.DEFAULT_CHANNELS
                                if (type == WsMessage.SUBSCRIBE) {
                                    broadcaster.subscribe(this, instanceId, channels)
                                } else {
                                    broadcaster.unsubscribe(this, instanceId, channels)
                                }
                            }
                            WsMessage.PING -> {
                                val pong = WsMessage(
                                    type = WsMessage.PONG,
                                    payload = json.encodeToJsonElement(buildJsonObject {}),
                                    timestamp = Clock.System.now(),
                                )
                                send(Frame.Text(json.encodeToString(WsMessage.serializer(), pong)))
                            }
                            WsMessage.CONSOLE_INPUT -> {
                                val instanceId = msg["instanceId"]?.jsonPrimitive?.content ?: continue
                                val command = msg["payload"]?.jsonObject?.get("command")?.jsonPrimitive?.content ?: continue
                                try {
                                    processManager.sendCommand(instanceId, command)
                                } catch (e: ApiException) {
                                    log.debug("console.input dropped for instance {}: {}", instanceId, e.code)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("Failed to parse WS message", e)
                    }
                }
            }
        } finally {
            broadcaster.removeSession(this)
        }
    }
}
