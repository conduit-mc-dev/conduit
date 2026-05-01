package dev.conduit.desktop.ui.instance

import dev.conduit.desktop.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class InstanceListViewModelTest {

    @Test
    fun `refresh sets error on failure`() = runBlocking {
        val httpClient = mockHttpClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/instances" -> respond(
                    mockErrorBody("UNAUTHORIZED", "Not authorized"),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    status = HttpStatusCode.Unauthorized,
                )
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val client = mockApiClient(httpClient)
        val vm = InstanceListViewModel(client)
        waitFor { !vm.state.value.isLoading }

        assertTrue(vm.state.value.instances.isEmpty())
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("加载实例列表失败"))
    }
}
