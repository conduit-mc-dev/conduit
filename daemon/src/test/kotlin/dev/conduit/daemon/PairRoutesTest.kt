package dev.conduit.daemon

import dev.conduit.core.model.PairConfirmRequest
import dev.conduit.core.model.PairConfirmResponse
import dev.conduit.core.model.PairInitiateResponse
import dev.conduit.core.model.PairedDevice
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairRoutesTest {

    @Test
    fun `setup mode allows initiate without auth`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val response = client.post("/api/v1/pair/initiate")
        assertEquals(HttpStatusCode.Created, response.status)

        val body = response.body<PairInitiateResponse>()
        assertEquals(6, body.code.length)
    }

    @Test
    fun `confirm pairing returns token`() = testApplication {
        setupTestModule()
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
        setupTestModule()
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
        setupTestModule()
        val client = jsonClient()

        val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = "First Device"))
        }

        val response = client.post("/api/v1/pair/initiate")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `initiate succeeds with valid token after pairing`() = testApplication {
        setupTestModule()
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
        setupTestModule()
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
    fun `revoke all devices returns 204 and invalidates tokens`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val code1 = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
        val token1 = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code1, deviceName = "Device 1"))
        }.body<PairConfirmResponse>().token

        val code2 = client.post("/api/v1/pair/initiate") {
            header(HttpHeaders.Authorization, "Bearer $token1")
        }.body<PairInitiateResponse>().code
        val token2 = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code2, deviceName = "Device 2"))
        }.body<PairConfirmResponse>().token

        val deleteResponse = client.delete("/api/v1/pair/devices") {
            header(HttpHeaders.Authorization, "Bearer $token1")
        }
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val list1 = client.get("/api/v1/pair/devices") {
            header(HttpHeaders.Authorization, "Bearer $token1")
        }
        assertEquals(HttpStatusCode.Unauthorized, list1.status)

        val list2 = client.get("/api/v1/pair/devices") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }
        assertEquals(HttpStatusCode.Unauthorized, list2.status)
    }

    @Test
    fun `rate limiting returns 429 after max attempts`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        client.post("/api/v1/pair/initiate")

        repeat(5) {
            client.post("/api/v1/pair/confirm") {
                contentType(ContentType.Application.Json)
                setBody(PairConfirmRequest(code = "000000", deviceName = "Test"))
            }
        }

        val response = client.post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = "000000", deviceName = "Test"))
        }
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `revoke device removes it`() = testApplication {
        setupTestModule()
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

        val listResponse = client.get("/api/v1/pair/devices") {
            header(HttpHeaders.Authorization, "Bearer ${confirmResult.token}")
        }
        assertEquals(HttpStatusCode.Unauthorized, listResponse.status)
    }
}
