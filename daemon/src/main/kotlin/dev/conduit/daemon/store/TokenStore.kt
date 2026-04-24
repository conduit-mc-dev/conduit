package dev.conduit.daemon.store

import dev.conduit.core.model.PairConfirmResponse
import dev.conduit.core.model.PairInitiateResponse
import dev.conduit.core.model.PairedDevice
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlin.time.Clock
import kotlin.time.Instant
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

class TokenStore {

    val daemonId: String = UUID.randomUUID().toString()

    private data class HashedToken(
        val tokenId: String,
        val deviceName: String,
        val tokenHash: ByteArray,
        val pairedAt: Instant,
        @Volatile var lastSeenAt: Instant,
    )

    private data class PendingCode(
        val code: String,
        val expiresAt: Instant,
    )

    private val tokens = ConcurrentHashMap<String, HashedToken>()
    private val pendingCode = AtomicReference<PendingCode?>(null)

    fun hasDevices(): Boolean = tokens.isNotEmpty()

    fun generatePairCode(): PairInitiateResponse {
        val code = IdGenerator.generatePairCode()
        val expiresAt = Clock.System.now() + 5.minutes
        pendingCode.set(PendingCode(code, expiresAt))
        return PairInitiateResponse(code = code, expiresAt = expiresAt)
    }

    fun confirmPairing(code: String, deviceName: String): PairConfirmResponse {
        val pending = pendingCode.get()
            ?: throw ApiException(HttpStatusCode.Unauthorized, "INVALID_PAIR_CODE", "No active pair code")

        if (Clock.System.now() > pending.expiresAt) {
            pendingCode.set(null)
            throw ApiException(HttpStatusCode.Unauthorized, "PAIR_CODE_EXPIRED", "Pair code has expired")
        }

        if (pending.code != code) {
            throw ApiException(HttpStatusCode.Unauthorized, "INVALID_PAIR_CODE", "Invalid pair code")
        }

        pendingCode.set(null)

        val rawToken = IdGenerator.generateToken()
        val tokenId = UUID.randomUUID().toString()
        val now = Clock.System.now()
        val hash = sha256(rawToken)

        tokens[tokenId] = HashedToken(
            tokenId = tokenId,
            deviceName = deviceName,
            tokenHash = hash,
            pairedAt = now,
            lastSeenAt = now,
        )

        return PairConfirmResponse(
            token = rawToken,
            tokenId = tokenId,
            daemonId = daemonId,
        )
    }

    fun validateToken(rawToken: String): String? {
        val hash = sha256(rawToken)
        for (entry in tokens.values) {
            if (MessageDigest.isEqual(hash, entry.tokenHash)) {
                entry.lastSeenAt = Clock.System.now()
                return entry.tokenId
            }
        }
        return null
    }

    fun listDevices(): List<PairedDevice> = tokens.values.map {
        PairedDevice(
            tokenId = it.tokenId,
            deviceName = it.deviceName,
            pairedAt = it.pairedAt,
            lastSeenAt = it.lastSeenAt,
        )
    }

    fun revokeDevice(tokenId: String) {
        tokens.remove(tokenId)
            ?: throw ApiException(HttpStatusCode.NotFound, "DEVICE_NOT_FOUND", "Device not found")
    }

    fun revokeAll() {
        tokens.clear()
    }

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
}
