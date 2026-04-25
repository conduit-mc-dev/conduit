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
import kotlin.test.assertTrue

class JavaRoutesTest {

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

    @Test
    fun `list java installations returns non-empty list`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/java/installations") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val installations = response.body<List<JavaInstallation>>()
        assertTrue(installations.isNotEmpty(), "Should detect at least one Java installation")
        assertTrue(installations.any { it.version.isNotBlank() })
    }

    @Test
    fun `set default java with invalid path returns 422`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.put("/api/v1/java/default") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SetDefaultJavaRequest(path = "/nonexistent/java"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `java routes require auth`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/java/installations")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
