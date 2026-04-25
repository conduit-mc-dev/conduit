package dev.conduit.daemon.routes

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.FileService
import dev.conduit.daemon.service.ServerPropertiesService
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

fun Route.fileRoutes(
    instanceStore: InstanceStore,
    fileService: FileService,
    serverPropertiesService: ServerPropertiesService,
) {
    route("/api/v1/instances/{id}") {
        get("/config/server-properties") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val props = serverPropertiesService.read(id)
            call.respond(JsonObject(props.mapValues { JsonPrimitive(it.value) }))
        }

        put("/config/server-properties") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val body = call.receive<JsonObject>()
            val changes = body.entries.associate { (k, v) ->
                k to (v.jsonPrimitive.contentOrNull ?: v.toString())
            }
            if (changes.isEmpty()) {
                call.respond(ServerPropertiesUpdateResponse(updated = emptyList(), restartRequired = false))
                return@put
            }
            val updated = serverPropertiesService.update(id, changes)
            call.respond(ServerPropertiesUpdateResponse(updated = updated, restartRequired = true))
        }

        get("/files") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val pathParam = call.queryParameters["path"] ?: ""
            val resolved = fileService.resolveSafePath(id, pathParam.ifEmpty { "." })
            if (!resolved.exists() || !resolved.isDirectory()) {
                throw ApiException(HttpStatusCode.NotFound, "FILE_NOT_FOUND", "Directory not found")
            }
            val entries = resolved.listDirectoryEntries().sorted().map { entry ->
                val lastModified = entry.getLastModifiedTime().toInstant()
                    .atOffset(ZoneOffset.UTC).format(ISO_FORMATTER)
                if (entry.isDirectory()) {
                    FileEntry(
                        name = entry.name,
                        type = "directory",
                        size = null,
                        lastModified = lastModified,
                    )
                } else {
                    FileEntry(
                        name = entry.name,
                        type = "file",
                        size = entry.fileSize(),
                        lastModified = lastModified,
                    )
                }
            }
            call.respond(DirectoryListing(path = pathParam.ifEmpty { "/" }, entries = entries))
        }

        get("/files/content") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val pathParam = call.queryParameters["path"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing path parameter")
            val resolved = fileService.resolveSafePath(id, pathParam)
            if (!resolved.exists() || resolved.isDirectory()) {
                throw ApiException(HttpStatusCode.NotFound, "FILE_NOT_FOUND", "File not found")
            }
            call.respondFile(resolved.toFile())
        }

        put("/files/content") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val pathParam = call.queryParameters["path"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing path parameter")
            fileService.validateNotProtected(pathParam)
            val resolved = fileService.resolveSafePath(id, pathParam)

            val bytes = call.receive<ByteArray>()
            if (bytes.size > FileService.MAX_FILE_SIZE) {
                throw ApiException(HttpStatusCode.PayloadTooLarge, "FILE_TOO_LARGE", "File exceeds 10 MB limit")
            }

            resolved.parent?.createDirectories()
            resolved.writeBytes(bytes)

            val lastModified = resolved.getLastModifiedTime().toInstant()
                .atOffset(ZoneOffset.UTC).format(ISO_FORMATTER)
            call.respond(
                FileWriteResponse(
                    path = pathParam,
                    size = bytes.size.toLong(),
                    lastModified = lastModified,
                )
            )
        }

        delete("/files/content") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val pathParam = call.queryParameters["path"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing path parameter")
            fileService.validateNotProtected(pathParam)
            val resolved = fileService.resolveSafePath(id, pathParam)
            if (!resolved.exists() || resolved.isDirectory()) {
                throw ApiException(HttpStatusCode.NotFound, "FILE_NOT_FOUND", "File not found")
            }
            resolved.deleteExisting()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
