package dev.conduit.daemon.service

import dev.conduit.core.model.WsMessage
import dev.conduit.core.model.WsMessage.Companion.CHANNEL_CONSOLE
import dev.conduit.core.model.WsMessage.Companion.CHANNEL_STATS
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

    companion object {
        // 需要显式订阅频道才接收的事件类型；其余事件自动广播给所有已连接 session
        private val CHANNEL_EVENT_TYPES = mapOf(
            WsMessage.CONSOLE_OUTPUT to CHANNEL_CONSOLE,
            WsMessage.SERVER_STATS to CHANNEL_STATS,
        )
    }

    private data class SessionState(
        val session: DefaultWebSocketServerSession,
        // instanceId -> 已订阅的 channel 集合
        val subscribedChannels: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    )

    private val mutex = Mutex()
    private val sessions = mutableListOf<SessionState>()

    suspend fun addSession(session: DefaultWebSocketServerSession) {
        mutex.withLock { sessions.add(SessionState(session)) }
    }

    suspend fun removeSession(session: DefaultWebSocketServerSession) {
        mutex.withLock { sessions.removeAll { it.session == session } }
    }

    suspend fun subscribe(session: DefaultWebSocketServerSession, instanceId: String, channels: List<String>) {
        mutex.withLock {
            val state = sessions.find { it.session == session } ?: return
            state.subscribedChannels.getOrPut(instanceId) { mutableSetOf() }.addAll(channels)
        }
    }

    suspend fun unsubscribe(session: DefaultWebSocketServerSession, instanceId: String, channels: List<String>) {
        mutex.withLock {
            val state = sessions.find { it.session == session } ?: return
            state.subscribedChannels[instanceId]?.removeAll(channels.toSet())
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
            val requiredChannel = CHANNEL_EVENT_TYPES[type]
            if (requiredChannel != null) {
                // 需显式订阅的事件：只发给订阅了对应 instanceId + channel 的 session
                sessions
                    .filter { it.subscribedChannels[instanceId]?.contains(requiredChannel) == true }
                    .map { it.session }
            } else {
                // 默认广播事件（state_changed, players_changed, task.*, instance.* 等）：发给所有 session
                sessions.map { it.session }
            }
        }
        sendToSessions(targets, text)
    }

    suspend fun broadcastGlobal(type: String, payload: JsonElement) {
        val message = WsMessage(
            type = type,
            instanceId = null,
            payload = payload,
            timestamp = Clock.System.now(),
        )
        val text = json.encodeToString(message)
        val targets = mutex.withLock { sessions.map { it.session } }
        sendToSessions(targets, text)
    }

    private suspend fun sendToSessions(targets: List<DefaultWebSocketServerSession>, text: String) {
        for (session in targets) {
            try {
                session.send(Frame.Text(text))
            } catch (e: Exception) {
                log.debug("Failed to send WS message to session, will be cleaned up", e)
            }
        }
    }
}
