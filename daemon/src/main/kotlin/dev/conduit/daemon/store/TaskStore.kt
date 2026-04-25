package dev.conduit.daemon.store

import dev.conduit.core.model.TaskCompletedPayload
import dev.conduit.core.model.TaskProgressPayload
import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.util.IdGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.concurrent.ConcurrentHashMap

data class TaskState(
    val taskId: String,
    val instanceId: String,
    val type: String,
    val progress: Double = 0.0,
    val message: String = "",
    val status: String = "running",
)

class TaskStore(
    private val broadcaster: WsBroadcaster,
    private val json: Json,
) {
    private val tasks = ConcurrentHashMap<String, TaskState>()

    fun create(instanceId: String, type: String, message: String): String {
        val taskId = IdGenerator.generateTaskId()
        tasks[taskId] = TaskState(
            taskId = taskId,
            instanceId = instanceId,
            type = type,
            message = message,
        )
        return taskId
    }

    suspend fun updateProgress(taskId: String, progress: Double, message: String) {
        tasks.compute(taskId) { _, state ->
            state?.copy(progress = progress, message = message)
        }
        val state = tasks[taskId] ?: return
        val payload = json.encodeToJsonElement(
            TaskProgressPayload(taskId = taskId, progress = progress, message = message)
        )
        broadcaster.broadcast(state.instanceId, WsMessage.TASK_PROGRESS, payload)
    }

    suspend fun complete(taskId: String, success: Boolean, message: String) {
        val state = tasks[taskId] ?: return
        tasks[taskId] = state.copy(
            status = if (success) "done" else "error",
            progress = if (success) 1.0 else state.progress,
            message = message,
        )
        val payload = json.encodeToJsonElement(
            TaskCompletedPayload(taskId = taskId, success = success, message = message)
        )
        broadcaster.broadcast(state.instanceId, WsMessage.TASK_COMPLETED, payload)
    }

    fun get(taskId: String): TaskState? = tasks[taskId]

    fun getByInstance(instanceId: String, type: String): TaskState? =
        tasks.values.firstOrNull { it.instanceId == instanceId && it.type == type && it.status == "running" }
}
