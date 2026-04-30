package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.testutil.forceInitializing
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoaderRoutesTest {

    @Test
    fun `get loader returns null when none installed`() = testApplication {
        setupTestModule()
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
        val env = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)
        env.forceInitializing(instance.id)

        val response = client.delete("/api/v1/instances/${instance.id}/loader") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `loader routes require auth`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/instances/fake/loader")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `get available loaders returns list`() = testApplication {
        setupTestModule(loaderHttpClient = createMockLoaderHttpClient())
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
    fun `available loaders includes Forge filtered by MC version`() = testApplication {
        setupTestModule(loaderHttpClient = createMockLoaderHttpClient())
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/loader/available") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val loaders = response.body<List<AvailableLoader>>()
        val forge = loaders.firstOrNull { it.type == LoaderType.FORGE }
        assertNotNull(forge, "Forge loader entry missing")
        assertEquals(setOf("1.20.4-49.0.3", "1.20.4-49.0.14"), forge.versions.toSet())
    }

    @Test
    fun `available loaders includes NeoForge mapped from MC version`() = testApplication {
        setupTestModule(loaderHttpClient = createMockLoaderHttpClient())
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/loader/available") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val loaders = response.body<List<AvailableLoader>>()
        val neoforge = loaders.firstOrNull { it.type == LoaderType.NEOFORGE }
        assertNotNull(neoforge, "NeoForge loader entry missing")
        assertEquals(setOf("20.4.80", "20.4.237", "20.4.238"), neoforge.versions.toSet())
    }

    @Test
    fun `install loader when initializing returns 409`() = testApplication {
        val env = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)
        env.forceInitializing(instance.id)

        val response = client.post("/api/v1/instances/${instance.id}/loader/install") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(InstallLoaderRequest(type = LoaderType.FABRIC, version = "0.16.14"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("SERVER_MUST_BE_STOPPED", response.body<ErrorResponse>().error.code)
    }
}
