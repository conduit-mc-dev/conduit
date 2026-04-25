package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    private lateinit var tempDir: Path

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
    }

    private suspend fun pairAndGetToken(client: io.ktor.client.HttpClient): String {
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        return client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "Test Device"))
        }.body<PairConfirmResponse>().token
    }

    private suspend fun createTestInstance(
        client: io.ktor.client.HttpClient,
        token: String,
    ): InstanceSummary {
        val instance = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Test Server", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()
        tempDir.resolve("instances").resolve(instance.id).createDirectories()
        return instance
    }

    @Test
    fun `health endpoint still works`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/public/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `server-json returns instance info`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/public/${instance.id}/server.json")
        assertEquals(HttpStatusCode.OK, response.status)

        val serverJson = response.body<ServerJson>()
        assertEquals(instance.id, serverJson.instanceId)
        assertEquals("Test Server", serverJson.serverName)
        assertEquals("1.20.4", serverJson.mcVersion)
        assertEquals(false, serverJson.online)
        assertEquals(0, serverJson.modCount)
        assertEquals(25565, serverJson.minecraft.port)
    }

    @Test
    fun `server-json returns 404 when public disabled`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        client.put("/api/v1/instances/${instance.id}/invite") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateInviteRequest(publicEndpointEnabled = false))
        }

        val response = client.get("/public/${instance.id}/server.json")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `server-json returns 404 for nonexistent instance`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/public/nonexistent/server.json")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `pack-mrpack returns 404 when not built`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/public/${instance.id}/pack.mrpack")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `pack-mrpack serves file after build`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", "PKfake-jar".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"test.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }

        client.post("/api/v1/instances/${instance.id}/pack/build") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BuildPackRequest(versionId = "1.0.0"))
        }
        delay(500)

        val response = client.get("/public/${instance.id}/pack.mrpack")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsBytes().isNotEmpty())
        assertNotNull(response.headers[HttpHeaders.ETag])
    }

    @Test
    fun `custom mod download serves file`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val fakeJar = "PKcustom-mod-download-test".toByteArray()
        client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJar, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"custom-mod.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }

        val response = client.get("/public/${instance.id}/mods/custom-mod.jar")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsBytes().isNotEmpty())
    }

    @Test
    fun `custom mod download 404 for nonexistent file`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/public/${instance.id}/mods/nonexistent.jar")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
