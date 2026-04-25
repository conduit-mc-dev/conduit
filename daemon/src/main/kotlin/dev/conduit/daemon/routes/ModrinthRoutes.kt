package dev.conduit.daemon.routes

import dev.conduit.core.download.ModrinthClient
import dev.conduit.daemon.ApiException
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.modrinthRoutes(modrinthClient: ModrinthClient) {
    route("/api/v1/modrinth") {
        get("/search") {
            val query = call.queryParameters["q"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing query parameter 'q'")
            val mcVersion = call.queryParameters["mcVersion"]
            val loader = call.queryParameters["loader"]
            val offset = call.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = call.queryParameters["limit"]?.toIntOrNull() ?: 20

            val result = modrinthClient.search(query, mcVersion, loader, offset, limit)
            call.respond(result)
        }

        get("/project/{projectId}/versions") {
            val projectId = call.parameters["projectId"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing projectId")
            val mcVersion = call.queryParameters["mcVersion"]
            val loader = call.queryParameters["loader"]

            val versions = modrinthClient.getProjectVersions(projectId, mcVersion, loader)
            call.respond(versions)
        }
    }
}
