package dev.conduit.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PairInitiateResponse(
    val code: String,
    val expiresAt: Instant,
)

@Serializable
data class PairConfirmRequest(
    val code: String,
    val deviceName: String,
)

@Serializable
data class PairConfirmResponse(
    val token: String,
    val tokenId: String,
    val daemonId: String,
)

@Serializable
data class PairedDevice(
    val tokenId: String,
    val deviceName: String,
    val pairedAt: Instant,
    val lastSeenAt: Instant,
)
