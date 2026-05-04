package dev.conduit.desktop.ui.instance

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class CreateInstanceViewModelTest {

    companion object {
        @JvmStatic @org.junit.jupiter.api.BeforeAll fun setup() = setupTestDispatchers()
    }

    private suspend fun CreateInstanceViewModel.awaitDone(timeoutMs: Long = 2000) {
        withTimeout(timeoutMs) {
            while (state.value.isCreating) delay(20)
        }
    }

    @Test
    fun `createInstance with empty name shows error`() = runBlocking {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        val vm = CreateInstanceViewModel(TEST_DAEMON_ID, mockDaemonManager(mockApiClient(httpClient)))

        var onSuccessCalled = false
        vm.create { onSuccessCalled = true }
        vm.awaitDone()

        assertFalse(onSuccessCalled)
        assertNotNull(vm.state.value.error)
    }
}
