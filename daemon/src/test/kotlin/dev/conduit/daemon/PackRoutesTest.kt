package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.store.BuildState
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackRoutesTest {

    @Test
    fun `get pack info returns 404 when never built`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/pack") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `build pack and check status`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val fakeJarBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "build-test-mod".toByteArray()
        client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"test-mod.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
                append("name", "Test Mod")
            }))
        }

        val buildResp = client.post("/api/v1/instances/${instance.id}/pack/build") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BuildPackRequest(versionId = "1.0.0"))
        }
        assertEquals(HttpStatusCode.Accepted, buildResp.status)
        val task = buildResp.body<TaskResponse>()
        assertEquals("pack_build", task.type)

        delay(500)

        val statusResp = client.get("/api/v1/instances/${instance.id}/pack/build/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, statusResp.status)
        val status = statusResp.body<BuildStatusResponse>()
        assertEquals(BuildState.DONE.value, status.state)

        val packResp = client.get("/api/v1/instances/${instance.id}/pack") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, packResp.status)
        val pack = packResp.body<PackInfo>()
        assertEquals("1.0.0", pack.versionId)
        assertEquals(false, pack.dirty)
        assertTrue(pack.fileSize > 0)
        assertEquals(1, pack.modCount)

        assertTrue(tempDir.resolve("instances/${instance.id}/pack/pack.mrpack").exists())
    }

    @Test
    fun `build status returns idle when never built`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/pack/build/status") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val status = response.body<BuildStatusResponse>()
        assertEquals(BuildState.IDLE.value, status.state)
    }

    @Test
    fun `pack routes require auth`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/instances/fake/pack")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
