package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PublicRoutesTest {

    @Test
    fun `health endpoint still works`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()

        val response = client.get("/public/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `server-json returns instance info`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

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
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

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
        val (tempDir) = setupTestModule()
        val client = jsonClient()

        val response = client.get("/public/nonexistent/server.json")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `pack-mrpack returns 404 when not built`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/public/${instance.id}/pack.mrpack")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `pack-mrpack serves file after build`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

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
        val bodyBytes = response.bodyAsBytes()
        assertTrue(bodyBytes.isNotEmpty())
        assertNotNull(response.headers[HttpHeaders.ETag])
        assertEquals(bodyBytes.size.toString(), response.headers[HttpHeaders.ContentLength])
    }

    @Test
    fun `custom mod download serves file`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

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
        val bodyBytes = response.bodyAsBytes()
        assertTrue(bodyBytes.isNotEmpty())
        assertEquals(bodyBytes.size.toString(), response.headers[HttpHeaders.ContentLength])
    }

    @Test
    fun `custom mod download 404 for nonexistent file`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/public/${instance.id}/mods/nonexistent.jar")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `server-json includes Cache-Control no-cache`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/public/${instance.id}/server.json")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `pack-mrpack returns 304 with matching If-None-Match`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", "PKetag-test-jar".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"etag.jar\"")
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

        val first = client.get("/public/${instance.id}/pack.mrpack")
        assertEquals(HttpStatusCode.OK, first.status)
        val etag = first.headers[HttpHeaders.ETag]
        assertNotNull(etag)

        val second = client.get("/public/${instance.id}/pack.mrpack") {
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.NotModified, second.status)
    }

    @Test
    fun `custom mod download returns 304 with matching If-None-Match`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", "PK304-test-mod".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"cached.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }

        val first = client.get("/public/${instance.id}/mods/cached.jar")
        assertEquals(HttpStatusCode.OK, first.status)
        val etag = first.headers[HttpHeaders.ETag]
        assertNotNull(etag)

        val second = client.get("/public/${instance.id}/mods/cached.jar") {
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.NotModified, second.status)
    }

    @Test
    fun `custom mod download rejects path traversal filename`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/public/${instance.id}/mods/..%2F..%2Fetc%2Fpasswd")
        assertTrue(response.status.value in listOf(404, 422))
    }
}
