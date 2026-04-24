package dev.conduit.daemon

import dev.conduit.core.model.ErrorBody
import dev.conduit.core.model.ErrorResponse
import dev.conduit.daemon.store.TokenStore
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.configurePlugins(tokenStore: TokenStore) {
    install(ContentNegotiation) {
        json(AppJson)
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.httpStatus,
                ErrorResponse(ErrorBody(cause.code, cause.message, cause.details))
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorBody("INTERNAL_ERROR", "An unexpected error occurred"))
            )
        }
    }

    install(Authentication) {
        bearer("bearer") {
            authenticate { credential ->
                tokenStore.validateToken(credential.token)?.let { tokenId ->
                    UserIdPrincipal(tokenId)
                }
            }
        }
    }

    install(WebSockets) {
        pingPeriodMillis = 30_000
        timeoutMillis = 90_000
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    install(CallLogging) {
        level = Level.INFO
    }
}
