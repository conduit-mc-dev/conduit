package dev.conduit.daemon.store

import dev.conduit.core.model.TaskCompletedPayload
import dev.conduit.core.model.TaskProgressPayload
import dev.conduit.core.model.WsMessage
import dev.conduit.daemon.service.WsBroadcaster
import dev.conduit.daemon.util.IdGenerator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.concurrent.ConcurrentHashMap

enum class TaskStatus(val value: String) {
    RUNNING("running"),
    DONE("done"),
    ERROR("error"),
}

data class TaskState(
    val taskId: String,
    val instanceId: String,
    val type: String,
    val progress: Double = 0.0,
    val message: String = "",
    val status: TaskStatus = TaskStatus.RUNNING,
)

class TaskStore(
    private val broadcaster: WsBroadcaster,
    private val json: Json,
) {
    companion object {
        const val TYPE_SERVER_JAR_DOWNLOAD = "server_jar_download"
        const val TYPE_LOADER_INSTALL = "loader_install"
        const val TYPE_PACK_BUILD = "pack_build"
    }

    private val tasks = ConcurrentHashMap<String, TaskState>()

    fun create(instanceId: String, type: String, message: String, taskId: String = IdGenerator.generateTaskId()): String {
        evictCompleted()
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
            status = if (success) TaskStatus.DONE else TaskStatus.ERROR,
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
        tasks.values.firstOrNull { it.instanceId == instanceId && it.type == type && it.status == TaskStatus.RUNNING }

    private fun evictCompleted() {
        if (tasks.size <= 100) return
        tasks.entries.removeIf { it.value.status in listOf(TaskStatus.DONE, TaskStatus.ERROR) }
    }
}
