package dev.conduit.desktop

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.api.ConduitWsClient
import dev.conduit.core.model.ErrorBody
import dev.conduit.core.model.ErrorResponse
import dev.conduit.core.model.WsMessage
import dev.conduit.desktop.session.DaemonManager
import dev.conduit.desktop.session.DaemonSession
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory

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

/**
 * Sets Dispatchers.Main to Unconfined for ViewModel tests.
 * Call in @BeforeAll companion, reset in @AfterAll.
 */
fun setupTestDispatchers() = Dispatchers.setMain(Dispatchers.Unconfined)
fun resetTestDispatchers() = Dispatchers.setMain(Dispatchers.Unconfined) // no-op for Unconfined

/**
 * Subscribes to a StateFlow (triggering WhileSubscribed sharing), waits for [condition],
 * then cancels. Use this instead of directly reading `.value` when the StateFlow uses
 * `SharingStarted.WhileSubscribed`.
 */
suspend fun <T> StateFlow<T>.awaitState(
    timeoutMs: Long = 3000,
    condition: (T) -> Boolean = { true },
): T {
    var result: T = this.value
    val job = CoroutineScope(Dispatchers.Unconfined).launch {
        collect { value ->
            result = value
            if (condition(value)) throw CancellationException("done")
        }
    }
    withTimeout(timeoutMs) {
        while (!condition(result)) delay(20)
    }
    job.cancel()
    return result
}

const val TEST_DAEMON_ID = "test-daemon"

/**
 * Creates a real DaemonManager with a real DaemonSession backed by [apiClient] (with mock HTTP engine).
 * The session's wsClient is replaced with a mock that emits from [messages].
 */
fun mockDaemonManager(
    apiClient: ConduitApiClient,
    messages: MutableSharedFlow<WsMessage> = MutableSharedFlow(extraBufferCapacity = 16),
): DaemonManager {
    val tempDir = createTempDirectory("conduit-test-mgr")
    val manager = DaemonManager(configDir = tempDir)

    // addDaemon with our mock apiClient injected via the optional parameter
    manager.addDaemon(
        daemonId = TEST_DAEMON_ID,
        daemonName = "Test Daemon",
        daemonUrl = "http://mock.local",
        token = "mock-token",
        apiClient = apiClient,
    )

    // Replace the real wsClient with a mock for controllable WS message emission
    val session = manager.getSession(TEST_DAEMON_ID)!!
    val mockWsClient = mockk<ConduitWsClient>(relaxed = true)
    every { mockWsClient.messages } returns messages
    every { mockWsClient.connect(any()) } answers {}
    coEvery { mockWsClient.subscribe(any(), any()) } coAnswers {}
    every { mockWsClient.close() } answers {}

    val wsField = DaemonSession::class.java.getDeclaredField("_wsClient")
    wsField.isAccessible = true
    wsField.set(session, mockWsClient)

    return manager
}
