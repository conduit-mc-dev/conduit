package dev.conduit.daemon.routes

import dev.conduit.daemon.ApiException
import io.ktor.http.*
import io.ktor.server.application.*

fun ApplicationCall.requireInstanceId(): String =
    parameters["id"]
        ?: throw ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Missing instance id")
