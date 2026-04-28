package dev.conduit.daemon

import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ModrinthRoutesTest {

    @Test
    fun `search requires query parameter`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/modrinth/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `modrinth routes require auth`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/modrinth/search?q=sodium")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `get project versions requires auth`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/modrinth/project/sodium/versions")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
