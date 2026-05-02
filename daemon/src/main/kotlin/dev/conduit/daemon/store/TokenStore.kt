package dev.conduit.daemon.store

import dev.conduit.core.model.PairConfirmResponse
import dev.conduit.core.model.PairInitiateResponse
import dev.conduit.core.model.PairedDevice
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes

class TokenStore(
    persistenceFile: Path? = null,
) {

    val daemonId: String

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

    @Serializable
    private data class PersistedToken(
        val tokenId: String,
        val deviceName: String,
        val tokenHashBase64: String,
        val pairedAt: Instant,
        val lastSeenAt: Instant,
    )

    @Serializable
    private data class PersistedData(
        val daemonId: String,
        val tokens: List<PersistedToken>,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tokensFile = persistenceFile?.toFile()

    private val tokens = ConcurrentHashMap<String, HashedToken>()
    private val pendingCode = AtomicReference<PendingCode?>(null)

    init {
        val loaded = loadFromFile()
        if (loaded != null) {
            daemonId = loaded.daemonId
            for (pt in loaded.tokens) {
                tokens[pt.tokenId] = HashedToken(
                    tokenId = pt.tokenId,
                    deviceName = pt.deviceName,
                    tokenHash = Base64.getDecoder().decode(pt.tokenHashBase64),
                    pairedAt = pt.pairedAt,
                    lastSeenAt = pt.lastSeenAt,
                )
            }
        } else {
            daemonId = UUID.randomUUID().toString()
            persist()
        }
    }

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

        persist()

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
        persist()
    }

    fun revokeAll() {
        tokens.clear()
        persist()
    }

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())

    private fun loadFromFile(): PersistedData? {
        val f = tokensFile ?: return null
        if (!f.exists()) return null
        return try {
            json.decodeFromString(PersistedData.serializer(), f.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun persist() {
        val f = tokensFile ?: return
        f.parentFile.mkdirs()
        val data = PersistedData(
            daemonId = daemonId,
            tokens = tokens.values.map {
                PersistedToken(
                    tokenId = it.tokenId,
                    deviceName = it.deviceName,
                    tokenHashBase64 = Base64.getEncoder().encodeToString(it.tokenHash),
                    pairedAt = it.pairedAt,
                    lastSeenAt = it.lastSeenAt,
                )
            },
        )
        f.writeText(json.encodeToString(data))
    }
}
