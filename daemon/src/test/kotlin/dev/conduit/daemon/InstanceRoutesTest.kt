package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstanceRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
    }

    /** 配对并返回 token，供后续请求使用 */
    private suspend fun pairAndGetToken(client: io.ktor.client.HttpClient): String {
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        return client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "Test Device"))
        }.body<PairConfirmResponse>().token
    }

    @Test
    fun `instances require authentication`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/instances")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `empty instance list`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val instances = response.body<List<InstanceSummary>>()
        assertEquals(0, instances.size)
    }

    @Test
    fun `create instance returns initializing state with taskId`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val createResponse = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Survival Server", mcVersion = "1.20.4"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val created = createResponse.body<InstanceSummary>()
        assertEquals("Survival Server", created.name)
        assertEquals("1.20.4", created.mcVersion)
        assertEquals(InstanceState.INITIALIZING, created.state)
        assertEquals(25565, created.mcPort)
        assertEquals(5, created.id.length)
        assertNotNull(created.taskId)
    }

    @Test
    fun `get instance by id`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val created = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Test Server", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()

        val getResponse = client.get("/api/v1/instances/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        val fetched = getResponse.body<InstanceSummary>()
        assertEquals(created.id, fetched.id)
        assertEquals(created.name, fetched.name)
    }

    @Test
    fun `update instance`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val created = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Old Name", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()

        val updateResponse = client.put("/api/v1/instances/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateInstanceRequest(name = "New Name", description = "A great server"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)

        val updated = updateResponse.body<InstanceSummary>()
        assertEquals("New Name", updated.name)
        assertEquals("A great server", updated.description)
    }

    @Test
    fun `delete stopped instance`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val created = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "To Delete", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()

        // 新建实例处于 INITIALIZING 状态，无法删除
        val deleteInitializing = client.delete("/api/v1/instances/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, deleteInitializing.status)
        val error = deleteInitializing.body<ErrorResponse>()
        assertEquals("INSTANCE_INITIALIZING", error.error.code)
    }

    @Test
    fun `duplicate name returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Unique Server", mcVersion = "1.20.4"))
        }

        val duplicateResponse = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Unique Server", mcVersion = "1.21.0"))
        }
        assertEquals(HttpStatusCode.Conflict, duplicateResponse.status)

        val error = duplicateResponse.body<ErrorResponse>()
        assertEquals("INSTANCE_NAME_CONFLICT", error.error.code)
    }

    @Test
    fun `auto port assignment increments`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val first = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Server A", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()

        val second = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Server B", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()

        assertEquals(25565, first.mcPort)
        assertEquals(25566, second.mcPort)
    }

    @Test
    fun `instance not found returns 404`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/instances/zzzzz") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)

        val error = response.body<ErrorResponse>()
        assertEquals("INSTANCE_NOT_FOUND", error.error.code)
    }
}
