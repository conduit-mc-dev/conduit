package dev.conduit.daemon.routes

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.service.ServerProcessManager
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.ModStore
import dev.conduit.daemon.store.PackStore
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes

fun Route.publicRoutes(
    instanceStore: InstanceStore,
    modStore: ModStore,
    packStore: PackStore,
    processManager: ServerProcessManager,
    dataDirectory: DataDirectory,
) {
    route("/public") {
        get("/health") {
            call.respond(mapOf("status" to "ok", "conduitVersion" to "0.1.0"))
        }

        get("/{instanceId}/server.json") {
            val instanceId = call.parameters["instanceId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instanceId")

            val instance = try {
                instanceStore.get(instanceId)
            } catch (_: Exception) {
                throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Instance not found")
            }

            if (!instanceStore.isPublicEndpointEnabled(instanceId)) {
                throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Public endpoint is disabled")
            }

            val mods = modStore.list(instanceId)
            val pack = packStore.get(instanceId)

            val serverJson = ServerJson(
                instanceId = instanceId,
                serverName = instance.name,
                serverDescription = instance.description,
                mcVersion = instance.mcVersion,
                loader = instance.loader?.let {
                    ServerJsonLoader(type = it.type.name.lowercase(), version = it.version)
                },
                modCount = mods.size,
                online = instance.state == InstanceState.RUNNING,
                playerCount = instance.playerCount,
                maxPlayers = instance.maxPlayers,
                pack = pack?.let { p ->
                    if (p.sha256 != null) {
                        ServerJsonPack(
                            versionId = p.versionId,
                            downloadUrl = "/public/$instanceId/pack.mrpack",
                            sha256 = p.sha256,
                            fileSize = p.fileSize,
                        )
                    } else null
                },
                minecraft = ServerJsonMinecraft(port = instance.mcPort),
            )

            call.response.header(HttpHeaders.CacheControl, "no-cache")
            pack?.sha256?.let { call.response.header(HttpHeaders.ETag, "\"$it\"") }
            call.respond(serverJson)
        }

        get("/{instanceId}/pack.mrpack") {
            val instanceId = call.parameters["instanceId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instanceId")

            instanceStore.get(instanceId)
            val mrpackPath = dataDirectory.packMrpackPath(instanceId)
            if (!mrpackPath.exists()) {
                throw ApiException(HttpStatusCode.NotFound, "PACK_NOT_BUILT", "No pack has been built yet")
            }

            val pack = packStore.get(instanceId)
            val etag = pack?.sha256?.let { "\"$it\"" }

            if (etag != null && call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }

            call.response.header(HttpHeaders.ContentType, "application/x-modrinth-modpack+zip")
            call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"pack.mrpack\"")
            etag?.let { call.response.header(HttpHeaders.ETag, it) }
            call.respondFile(mrpackPath.toFile())
        }

        get("/{instanceId}/mods/{fileName}") {
            val instanceId = call.parameters["instanceId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instanceId")
            val fileName = call.parameters["fileName"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing fileName")

            instanceStore.get(instanceId)
            val customModPath = dataDirectory.modsCustomDir(instanceId).resolve(fileName)
            if (!customModPath.exists()) {
                throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Custom mod not found")
            }

            val mod = modStore.list(instanceId).firstOrNull {
                it.source == "custom" && it.fileName == fileName
            }
            val etag = mod?.hashes?.sha512?.let { "\"$it\"" }

            if (etag != null && call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }

            call.response.header(HttpHeaders.ContentType, "application/java-archive")
            etag?.let { call.response.header(HttpHeaders.ETag, it) }
            call.respondFile(customModPath.toFile())
        }
    }
}
