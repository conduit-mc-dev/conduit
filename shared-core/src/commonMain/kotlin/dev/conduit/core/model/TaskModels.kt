package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TaskResponse(
    val taskId: String,
    val type: String,
    val message: String,
)

@Serializable
data class TaskProgressPayload(
    val taskId: String,
    val progress: Double,
    val message: String,
)

@Serializable
data class TaskCompletedPayload(
    val taskId: String,
    val success: Boolean,
    val message: String,
)
