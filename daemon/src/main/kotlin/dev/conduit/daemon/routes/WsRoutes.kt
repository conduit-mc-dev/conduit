package dev.conduit.daemon.routes

import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.store.TokenStore
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dev.conduit.daemon.routes.WsRoutes")

fun Route.wsRoutes(broadcaster: WsBroadcaster, tokenStore: TokenStore, json: Json) {
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
                        val instanceId = msg["instanceId"]?.jsonPrimitive?.content ?: continue

                        when (type) {
                            WsMessage.SUBSCRIBE -> broadcaster.subscribe(this, instanceId)
                            WsMessage.UNSUBSCRIBE -> broadcaster.unsubscribe(this, instanceId)
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
