package dev.conduit.desktop

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.ErrorBody
import dev.conduit.core.model.ErrorResponse
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

val TestJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun mockHttpClient(handler: MockRequestHandler): HttpClient =
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(TestJson) }
        expectSuccess = false
    }

fun mockApiClient(httpClient: HttpClient): ConduitApiClient =
    ConduitApiClient(
        baseUrl = "http://mock.local",
        token = "mock-token",
        httpClient = httpClient,
    )

fun mockJsonBody(body: Any): String = TestJson.encodeToString(body)

fun mockErrorBody(code: String, message: String): String =
    TestJson.encodeToString(ErrorResponse(ErrorBody(code = code, message = message)))

suspend fun waitFor(timeoutMs: Long = 2000, condition: () -> Boolean) {
    withTimeout(timeoutMs) {
        while (!condition()) delay(20)
    }
}
