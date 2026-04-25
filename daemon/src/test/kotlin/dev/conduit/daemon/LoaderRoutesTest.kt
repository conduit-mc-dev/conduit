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
import kotlin.test.assertNull

class LoaderRoutesTest {

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

    private suspend fun pairAndGetToken(client: io.ktor.client.HttpClient): String {
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        return client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "Test Device"))
        }.body<PairConfirmResponse>().token
    }

    private suspend fun createStoppedInstance(
        client: io.ktor.client.HttpClient,
        token: String,
    ): InstanceSummary {
        return client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Test Server", mcVersion = "1.20.4"))
        }.body()
    }

    @Test
    fun `get loader returns null when none installed`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createStoppedInstance(client, token)

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
        val instance = createStoppedInstance(client, token)

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
}
