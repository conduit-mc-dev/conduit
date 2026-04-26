package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoaderRoutesTest {

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
    }

    @Test
    fun `get loader returns null when none installed`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/loader") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `uninstall loader when instance initializing returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.delete("/api/v1/instances/${instance.id}/loader") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `loader routes require auth`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/instances/fake/loader")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `get available loaders returns list`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/loader/available") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val loaders = response.body<List<AvailableLoader>>()
        assertTrue(loaders.isNotEmpty())
    }

    @Test
    fun `install loader when initializing returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.post("/api/v1/instances/${instance.id}/loader/install") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(InstallLoaderRequest(type = LoaderType.FABRIC, version = "0.16.14"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("SERVER_MUST_BE_STOPPED", response.body<ErrorResponse>().error.code)
    }
}
