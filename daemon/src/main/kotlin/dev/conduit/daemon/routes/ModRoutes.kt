package dev.conduit.daemon.routes

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.ModService
import dev.conduit.daemon.store.InstanceStore
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

fun Route.modRoutes(
    instanceStore: InstanceStore,
    modService: ModService,
) {
    route("/api/v1/instances/{id}/mods") {
        get {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            call.respond(modService.listMods(id))
        }

        post {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val request = call.receive<InstallModRequest>()
            val mod = modService.installFromModrinth(id, request.modrinthVersionId)
            call.respond(HttpStatusCode.Created, mod)
        }

        post("/upload") {
            val id = call.requireInstanceId()
            instanceStore.get(id)

            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var fileName: String? = null
            var name: String? = null
            var version: String? = null
            var env: ModEnvSupport? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName
                        fileBytes = part.provider().toByteArray()
                    }
                    is PartData.FormItem -> {
                        when (part.name) {
                            "name" -> name = part.value
                            "version" -> version = part.value
                            "env" -> env = try {
                                kotlinx.serialization.json.Json.decodeFromString<ModEnvSupport>(part.value)
                            } catch (_: Exception) { null }
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (fileBytes == null || fileName == null) {
                throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing file in multipart upload")
            }

            val mod = modService.uploadCustomMod(id, fileName!!, fileBytes!!, name, version, env)
            call.respond(HttpStatusCode.Created, mod)
        }

        delete("/{modId}") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val modId = call.parameters["modId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing modId")
            modService.removeMod(id, modId)
            call.respond(HttpStatusCode.NoContent)
        }

        put("/{modId}") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val modId = call.parameters["modId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing modId")
            val request = call.receive<UpdateModRequest>()
            val mod = modService.updateMod(id, modId, request.modrinthVersionId)
            call.respond(mod)
        }

        patch("/{modId}") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val modId = call.parameters["modId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing modId")
            val request = call.receive<ToggleModRequest>()
            val mod = modService.toggleMod(id, modId, request.enabled)
            call.respond(mod)
        }

        get("/updates") {
            val id = call.requireInstanceId()
            instanceStore.get(id)
            val result = modService.checkUpdates(id)
            call.respond(result)
        }
    }
}
