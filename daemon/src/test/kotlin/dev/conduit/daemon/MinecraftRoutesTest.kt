package dev.conduit.daemon

import dev.conduit.core.model.MinecraftVersion
import dev.conduit.core.model.PairConfirmRequest
import dev.conduit.core.model.PairInitiateResponse
import dev.conduit.core.model.PairConfirmResponse
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

class MinecraftRoutesTest {

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
    fun `minecraft versions require authentication`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/minecraft/versions")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `minecraft versions returns list from Mojang`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/minecraft/versions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val versions = response.body<List<MinecraftVersion>>()
        assertTrue(versions.isNotEmpty())
        assertTrue(versions.all { it.type == "release" })
    }
}
