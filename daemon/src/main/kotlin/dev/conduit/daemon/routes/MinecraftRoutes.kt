package dev.conduit.daemon.routes

import dev.conduit.core.download.MojangClient
import dev.conduit.core.model.MinecraftVersionsResponse
import dev.conduit.daemon.ApiException
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.minecraftRoutes(mojangClient: MojangClient) {
    route("/api/v1/minecraft") {
        get("/versions") {
            val type = call.request.queryParameters["type"] ?: "release"
            try {
                val versions = mojangClient.listVersions(type)
                val cachedAt = mojangClient.cachedAt?.toString() ?: kotlin.time.Clock.System.now().toString()
                call.respond(MinecraftVersionsResponse(versions = versions, cachedAt = cachedAt))
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                throw ApiException(HttpStatusCode.BadGateway, "MOJANG_API_ERROR", "Failed to fetch Minecraft versions: ${e.message}")
            }
        }
    }
}
