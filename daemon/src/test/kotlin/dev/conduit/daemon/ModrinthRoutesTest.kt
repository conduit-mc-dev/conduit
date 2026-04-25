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

class ModrinthRoutesTest {

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
    fun `search requires query parameter`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/modrinth/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `modrinth routes require auth`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/modrinth/search?q=sodium")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
