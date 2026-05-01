package dev.conduit.desktop.ui.instance

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CreateInstanceViewModelTest {

    @Test
    fun `createInstance with empty name shows error`() = runBlocking {
        val httpClient = mockHttpClient { respondError(HttpStatusCode.NotFound) }
        val client = mockApiClient(httpClient)
        val vm = CreateInstanceViewModel(client)
        waitFor { !vm.state.value.isLoadingVersions }

        var onSuccessCalled = false
        vm.createInstance { onSuccessCalled = true }
        waitFor { !vm.state.value.isCreating }

        assertFalse(onSuccessCalled)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("请输入实例名称"))
    }
}
