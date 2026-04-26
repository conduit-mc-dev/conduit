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
import kotlin.test.assertNotNull

class InstanceRoutesTest {

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
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

        val created = createTestInstance(client, token)

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

        val created = createTestInstance(client, token, name = "Old Name")

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

        val created = createTestInstance(client, token, name = "To Delete")

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

        createTestInstance(client, token, name = "Unique Server")

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

        val first = createTestInstance(client, token, name = "Server A")
        val second = createTestInstance(client, token, name = "Server B")

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

    @Test
    fun `explicit mcPort creates instance with that port`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Custom Port", mcVersion = "1.20.4", mcPort = 30000))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(30000, response.body<InstanceSummary>().mcPort)
    }

    @Test
    fun `duplicate explicit port returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Port A", mcVersion = "1.20.4", mcPort = 30000))
        }

        val response = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Port B", mcVersion = "1.20.4", mcPort = 30000))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("PORT_CONFLICT", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `update nonexistent instance returns 404`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.put("/api/v1/instances/zzzzz") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateInstanceRequest(name = "Nope"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("INSTANCE_NOT_FOUND", response.body<ErrorResponse>().error.code)
    }

    @Test
    fun `update instance name conflict returns 409`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        createTestInstance(client, token, name = "Server A")
        val b = createTestInstance(client, token, name = "Server B")

        val response = client.put("/api/v1/instances/${b.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateInstanceRequest(name = "Server A"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("INSTANCE_NAME_CONFLICT", response.body<ErrorResponse>().error.code)
    }
}
