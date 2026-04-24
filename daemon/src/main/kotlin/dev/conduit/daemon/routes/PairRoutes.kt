package dev.conduit.daemon.routes

import dev.conduit.core.model.PairConfirmRequest
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.TokenStore
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pairRoutes(tokenStore: TokenStore) {
    route("/api/v1/pair") {
        post("/initiate") {
            if (tokenStore.hasDevices()) {
                val rawToken = call.request.header(HttpHeaders.Authorization)
                    ?.removePrefix("Bearer ")
                    ?: throw ApiException(HttpStatusCode.Unauthorized, "AUTH_REQUIRED", "Authentication required")
                tokenStore.validateToken(rawToken)
                    ?: throw ApiException(HttpStatusCode.Unauthorized, "AUTH_INVALID", "Invalid token")
            }
            call.respond(HttpStatusCode.Created, tokenStore.generatePairCode())
        }

        post("/confirm") {
            val request = call.receive<PairConfirmRequest>()
            call.respond(tokenStore.confirmPairing(request.code, request.deviceName))
        }

        authenticate("bearer") {
            get("/devices") {
                call.respond(tokenStore.listDevices())
            }

            delete("/devices/{tokenId}") {
                val tokenId = call.parameters["tokenId"]
                    ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing tokenId")
                tokenStore.revokeDevice(tokenId)
                call.respond(HttpStatusCode.NoContent)
            }

            delete("/devices") {
                tokenStore.revokeAll()
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
