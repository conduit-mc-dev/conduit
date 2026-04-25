package dev.conduit.daemon.service

import dev.conduit.daemon.ApiException
import io.ktor.http.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RateLimiter(
    private val maxAttempts: Int = 5,
    private val windowDuration: kotlin.time.Duration = 1.minutes,
) {
    private val attempts = ConcurrentHashMap<String, MutableList<Instant>>()

    fun check(key: String) {
        val now = Clock.System.now()
        val cutoff = now - windowDuration
        val list = attempts.computeIfAbsent(key) { mutableListOf() }
        synchronized(list) {
            list.removeAll { it < cutoff }
            if (list.size >= maxAttempts) {
                throw ApiException(HttpStatusCode.TooManyRequests, "RATE_LIMITED", "Too many attempts, please try again later")
            }
            list.add(now)
        }
        // 定期清理空条目，防止内存泄漏
        if (attempts.size > 100) {
            attempts.entries.removeIf { (_, v) -> synchronized(v) { v.isEmpty() } }
        }
    }
}
