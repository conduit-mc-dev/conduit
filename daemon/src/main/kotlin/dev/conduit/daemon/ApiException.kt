package dev.conduit.daemon

import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

class ApiException(
    val httpStatus: HttpStatusCode,
    val code: String,
    override val message: String,
    val details: JsonElement? = null,
) : RuntimeException(message)
