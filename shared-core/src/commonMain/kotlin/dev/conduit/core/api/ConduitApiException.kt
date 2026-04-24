package dev.conduit.core.api

import dev.conduit.core.model.ErrorResponse

class ConduitApiException(
    val httpStatus: Int,
    val errorResponse: ErrorResponse? = null,
    override val message: String = errorResponse?.error?.message ?: "HTTP $httpStatus",
) : RuntimeException(message)
