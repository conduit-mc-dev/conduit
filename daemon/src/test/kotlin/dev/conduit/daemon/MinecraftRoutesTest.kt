package dev.conduit.daemon

import dev.conduit.core.model.MinecraftVersionsResponse
import dev.conduit.daemon.service.DataDirectory
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MinecraftRoutesTest {

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            val tempDir = Files.createTempDirectory("conduit-test")
            tempDir.toFile().deleteOnExit()
            module(dataDirectory = DataDirectory(tempDir))
        }
    }

    @Test
    fun `minecraft versions require authentication`() = testApplication {
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/minecraft/versions")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `minecraft versions returns wrapped response from Mojang`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/minecraft/versions") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<MinecraftVersionsResponse>()
        assertTrue(body.versions.isNotEmpty())
        assertTrue(body.versions.all { it.type == "release" })
        assertNotNull(body.cachedAt)
    }

    @Test
    fun `minecraft versions supports type filter`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/minecraft/versions?type=all") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<MinecraftVersionsResponse>()
        assertTrue(body.versions.isNotEmpty())
        val types = body.versions.map { it.type }.toSet()
        assertTrue(types.size > 1, "Expected multiple version types with type=all, got: $types")
    }
}
