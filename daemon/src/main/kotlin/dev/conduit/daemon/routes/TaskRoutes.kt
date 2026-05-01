package dev.conduit.daemon.routes

import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.LoaderService
import dev.conduit.daemon.service.PackService
import dev.conduit.daemon.service.ServerJarService
import dev.conduit.daemon.store.TaskStatus
import dev.conduit.daemon.store.TaskStore
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.taskRoutes(
    taskStore: TaskStore,
    loaderService: LoaderService,
    packService: PackService,
    serverJarService: ServerJarService,
) {
    post("/api/v1/tasks/{taskId}/cancel") {
        val taskId = call.parameters["taskId"]!!
        val task = taskStore.get(taskId)
            ?: throw ApiException(HttpStatusCode.NotFound, "TASK_NOT_FOUND", "Task not found")
        if (task.status != TaskStatus.RUNNING) {
            throw ApiException(
                HttpStatusCode.Conflict,
                "TASK_NOT_CANCELLABLE",
                "Task is not in a cancellable state",
            )
        }
        when (task.type) {
            TaskStore.TYPE_LOADER_INSTALL -> loaderService.cancelInstall(taskId)
            TaskStore.TYPE_PACK_BUILD -> packService.cancelBuild(taskId)
            TaskStore.TYPE_SERVER_JAR_DOWNLOAD -> serverJarService.cancelDownload(taskId)
        }
        call.respond(HttpStatusCode.OK, mapOf("cancelled" to true))
    }
}
