package dev.conduit.daemon.service

import dev.conduit.core.model.WsMessage
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import kotlin.time.Clock

class WsBroadcaster(private val json: Json) {

    private val log = LoggerFactory.getLogger(WsBroadcaster::class.java)

    private data class Subscription(
        val session: DefaultWebSocketServerSession,
        val instanceIds: MutableSet<String> = mutableSetOf(),
    )

    private val mutex = Mutex()
    private val sessions = mutableListOf<Subscription>()

    suspend fun addSession(session: DefaultWebSocketServerSession) {
        mutex.withLock { sessions.add(Subscription(session)) }
    }

    suspend fun removeSession(session: DefaultWebSocketServerSession) {
        mutex.withLock { sessions.removeAll { it.session == session } }
    }

    suspend fun subscribe(session: DefaultWebSocketServerSession, instanceId: String) {
        mutex.withLock {
            sessions.find { it.session == session }?.instanceIds?.add(instanceId)
        }
    }

    suspend fun unsubscribe(session: DefaultWebSocketServerSession, instanceId: String) {
        mutex.withLock {
            sessions.find { it.session == session }?.instanceIds?.remove(instanceId)
        }
    }

    suspend fun broadcast(instanceId: String, type: String, payload: JsonElement) {
        val message = WsMessage(
            type = type,
            instanceId = instanceId,
            payload = payload,
            timestamp = Clock.System.now(),
        )
        val text = json.encodeToString(message)
        val targets = mutex.withLock {
            sessions.filter { instanceId in it.instanceIds }.map { it.session }
        }
        for (session in targets) {
            try {
                session.send(Frame.Text(text))
            } catch (e: Exception) {
                log.debug("Failed to send WS message to session, will be cleaned up", e)
            }
        }
    }
}
