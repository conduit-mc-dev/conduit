package dev.conduit.daemon.util

import java.security.SecureRandom

object IdGenerator {

    private val random = SecureRandom()
    private const val BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz"
    private const val BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    fun generateInstanceId(): String = buildString(5) {
        repeat(5) { append(BASE36[random.nextInt(BASE36.length)]) }
    }

    fun generateToken(): String = buildString(72) {
        append("conduit_")
        repeat(64) { append(BASE62[random.nextInt(BASE62.length)]) }
    }

    fun generatePairCode(): String =
        String.format("%06d", random.nextInt(1_000_000))
}
