package dev.conduit.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ErrorResponse(val error: ErrorBody)

@Serializable
data class ErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

@Serializable
data class FieldError(
    val field: String,
    val reason: String,
)
