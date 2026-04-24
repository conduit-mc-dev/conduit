package dev.conduit.daemon

import dev.conduit.core.model.PairConfirmRequest
import dev.conduit.core.model.PairConfirmResponse
import dev.conduit.core.model.PairInitiateResponse
import dev.conduit.core.model.PairedDevice
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    @Test
    fun `setup mode allows initiate without auth`() = testApplication {
        application { module() }
        val client = jsonClient()

        val response = client.post("/api/v1/pair/initiate")
        assertEquals(HttpStatusCode.Created, response.status)

        val body = response.body<PairInitiateResponse>()
        assertEquals(6, body.code.length)
    }

    @Test
    fun `confirm pairing returns token`() = testApplication {
        application { module() }
        val client = jsonClient()

        val initResponse = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>()

        val confirmResponse = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = initResponse.code, deviceName = "Test Device"))
        }
        assertEquals(HttpStatusCode.OK, confirmResponse.status)

        val body = confirmResponse.body<PairConfirmResponse>()
        assertTrue(body.token.startsWith("conduit_"))
        assertEquals(72, body.token.length)
        assertTrue(body.tokenId.isNotBlank())
        assertTrue(body.daemonId.isNotBlank())
    }

    @Test
    fun `invalid pair code returns 401`() = testApplication {
        application { module() }
        val client = jsonClient()

        client.post("/api/v1/pair/initiate")

        val response = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = "000000", deviceName = "Test Device"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `initiate requires auth after first device paired`() = testApplication {
        application { module() }
        val client = jsonClient()

        // 配对第一台设备
        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "First Device"))
        }

        // 再次 initiate 不带 token → 401
        val response = client.post("/api/v1/pair/initiate")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `initiate succeeds with valid token after pairing`() = testApplication {
        application { module() }
        val client = jsonClient()

        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        val token = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "First Device"))
        }.body<PairConfirmResponse>().token

        val response = client.post("/api/v1/pair/initiate") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `list devices returns paired devices`() = testApplication {
        application { module() }
        val client = jsonClient()

        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        val token = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "My Laptop"))
        }.body<PairConfirmResponse>().token

        val response = client.get("/api/v1/pair/devices") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val devices = response.body<List<PairedDevice>>()
        assertEquals(1, devices.size)
        assertEquals("My Laptop", devices[0].deviceName)
    }

    @Test
    fun `revoke device removes it`() = testApplication {
        application { module() }
        val client = jsonClient()

        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        val confirmResult = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "My Laptop"))
        }.body<PairConfirmResponse>()

        val deleteResponse = client.delete("/api/v1/pair/devices/${confirmResult.tokenId}") {
            header(HttpHeaders.Authorization, "Bearer ${confirmResult.token}")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // token 已失效，列表应返回 401
        val listResponse = client.get("/api/v1/pair/devices") {
            header(HttpHeaders.Authorization, "Bearer ${confirmResult.token}")
        }
        assertEquals(HttpStatusCode.Unauthorized, listResponse.status)
    }
}
