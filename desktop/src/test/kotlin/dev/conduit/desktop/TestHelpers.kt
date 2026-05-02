package dev.conduit.desktop

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitWsClient
import dev.conduit.core.model.ErrorBody
import dev.conduit.core.model.ErrorResponse
import dev.conduit.core.model.WsMessage
import dev.conduit.desktop.session.SessionManager
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

inline fun <reified T> mockJsonBody(body: T): String = TestJson.encodeToString(body)

fun mockErrorBody(code: String, message: String): String =
    TestJson.encodeToString(ErrorResponse(ErrorBody(code = code, message = message)))

suspend fun waitFor(timeoutMs: Long = 2000, condition: () -> Boolean) {
    withTimeout(timeoutMs) {
        while (!condition()) delay(20)
    }
}

fun mockSession(
    apiClient: ConduitApiClient,
    messages: MutableSharedFlow<WsMessage> = MutableSharedFlow(extraBufferCapacity = 16),
): SessionManager {
    val wsClient = mockk<ConduitWsClient>(relaxed = true)
    every { wsClient.connect(any()) } answers {}
    coEvery { wsClient.subscribe(any(), any()) } coAnswers { }
    every { wsClient.messages } returns messages
    every { wsClient.close() } answers {}

    val session = mockk<SessionManager>(relaxed = true)
    every { session.wsClient } returns wsClient
    every { session.isActive } returns true
    every { session.getConsoleLines(any()) } answers {
        val key = firstArg<String>()
        consoleBuffers.getOrPut(key) { MutableStateFlow(emptyList()) }
    }
    every { session.appendConsoleLine(any(), any()) } answers {
        val instanceId = firstArg<String>()
        val line = secondArg<String>()
        val buffer = consoleBuffers.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
        val list = buffer.value.toMutableList()
        list.add(line)
        if (list.size > 1000) list.removeAt(0)
        buffer.value = list
    }
    every { session.start(any()) } answers {
        wsClient
    }
    every { session.stop() } answers {
        consoleBuffers.clear()
    }
    return session
}

private val consoleBuffers = mutableMapOf<String, MutableStateFlow<List<String>>>()
