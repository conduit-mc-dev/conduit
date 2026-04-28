package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.FileService
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileRoutesTest {

    // --- server.properties ---

    @Test
    fun `get server-properties returns empty when file missing`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/config/server-properties") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val props = response.body<JsonObject>()
        assertTrue(props.isEmpty())
    }

    @Test
    fun `update server-properties creates file and returns updated keys`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/config/server-properties") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"max-players": "30", "motd": "Hello"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<ServerPropertiesUpdateResponse>()
        assertTrue(result.updated.containsAll(listOf("max-players", "motd")))
        assertTrue(result.restartRequired)

        val getResp = client.get("/api/v1/instances/${instance.id}/config/server-properties") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val reloaded = getResp.body<JsonObject>()
        assertEquals("30", reloaded["max-players"]?.jsonPrimitive?.content)
        assertEquals("Hello", reloaded["motd"]?.jsonPrimitive?.content)
    }

    // --- File listing ---

    @Test
    fun `list root directory of instance`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val instanceDir = tempDir.resolve("instances").resolve(instance.id)
        instanceDir.resolve("test.txt").writeText("hello")
        instanceDir.resolve("config").createDirectories()

        val response = client.get("/api/v1/instances/${instance.id}/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val listing = response.body<DirectoryListing>()
        assertEquals("/", listing.path)
        assertTrue(listing.entries.any { it.name == "test.txt" && it.type == "file" })
        assertTrue(listing.entries.any { it.name == "config" && it.type == "directory" })
    }

    @Test
    fun `list subdirectory`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val configDir = tempDir.resolve("instances").resolve(instance.id).resolve("config")
        configDir.createDirectories()
        configDir.resolve("opts.json").writeText("{}")

        val response = client.get("/api/v1/instances/${instance.id}/files?path=config") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val listing = response.body<DirectoryListing>()
        assertEquals("config", listing.path)
        assertTrue(listing.entries.any { it.name == "opts.json" })
    }

    @Test
    fun `list nonexistent directory returns 404`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files?path=nonexistent") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- File read ---

    @Test
    fun `read file content`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val instanceDir = tempDir.resolve("instances").resolve(instance.id)
        instanceDir.resolve("ops.json").writeText("""[{"name":"player1"}]""")

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=ops.json") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("player1"))
    }

    @Test
    fun `read nonexistent file returns 404`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=nope.txt") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- File write ---

    @Test
    fun `write file creates file and parent dirs`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=config/new.json") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"key": "value"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val result = response.body<FileWriteResponse>()
        assertEquals("config/new.json", result.path)
        assertTrue(result.size > 0)

        val written = tempDir.resolve("instances").resolve(instance.id).resolve("config/new.json").readText()
        assertTrue(written.contains("value"))
    }

    // --- File delete ---

    @Test
    fun `delete file returns 204`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val instanceDir = tempDir.resolve("instances").resolve(instance.id)
        instanceDir.resolve("deleteme.txt").writeText("bye")

        val response = client.delete("/api/v1/instances/${instance.id}/files/content?path=deleteme.txt") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        assertTrue(!instanceDir.resolve("deleteme.txt").exists())
    }

    // --- Safety ---

    @Test
    fun `path traversal is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=../../../etc/passwd") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `write to protected file server-jar is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=server.jar") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("fake jar")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `write to mods jar is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=mods/evil.jar") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("fake")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `write to pack directory is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=pack/something") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("fake")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `delete protected instance-json is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.delete("/api/v1/instances/${instance.id}/files/content?path=instance.json") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `PUT path traversal is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=../../../etc/passwd") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("malicious content")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `DELETE path traversal is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.delete("/api/v1/instances/${instance.id}/files/content?path=../../../etc/passwd") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `directory listing path traversal is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files?path=../../../") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `backslash path traversal is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=..\\..\\..\\etc\\passwd") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("malicious")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `absolute path is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=/etc/passwd") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET server-jar is blocked by validateNotProtected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)
        tempDir.resolve("instances/${instance.id}/server.jar").toFile().also {
            it.parentFile.mkdirs()
            it.writeText("fake")
        }

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=server.jar") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET instance-json is blocked`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=instance.json") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET file under mods-disabled is blocked`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=mods-disabled/foo.jar") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET mods jar file is blocked`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files/content?path=mods/something.jar") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET listing pack directory is blocked`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)
        tempDir.resolve("instances/${instance.id}/pack").createDirectories()

        val response = client.get("/api/v1/instances/${instance.id}/files?path=pack") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET listing mods-custom directory is blocked`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)
        tempDir.resolve("instances/${instance.id}/mods-custom").createDirectories()

        val response = client.get("/api/v1/instances/${instance.id}/files?path=mods-custom") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `GET listing root still works`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/files") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `delete nonexistent file returns 404`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.delete("/api/v1/instances/${instance.id}/files/content?path=ghost.txt") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `write file exceeding size limit returns 413`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val bigBody = ByteArray((FileService.MAX_FILE_SIZE + 1).toInt()) { 0x41 }
        val response = client.put("/api/v1/instances/${instance.id}/files/content?path=big.bin") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.OctetStream)
            setBody(bigBody)
        }
        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
    }
}
