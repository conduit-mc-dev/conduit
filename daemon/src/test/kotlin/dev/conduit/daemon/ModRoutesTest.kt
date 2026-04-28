package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModRoutesTest {

    @Test
    fun `list mods returns empty for new instance`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val mods = response.body<List<InstalledMod>>()
        assertTrue(mods.isEmpty())
    }

    @Test
    fun `upload custom mod and list`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val fakeJarBytes = "PKfake-jar-content-for-testing".toByteArray()

        val response = client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"test-mod.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
                append("name", "Test Mod")
                append("version", "1.0.0")
            }))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val mod = response.body<InstalledMod>()
        assertEquals("Test Mod", mod.name)
        assertEquals("1.0.0", mod.version)
        assertEquals("custom", mod.source)
        assertEquals("test-mod.jar", mod.fileName)
        assertTrue(mod.enabled)
        assertNotNull(mod.hashes.sha1)
        assertNotNull(mod.hashes.sha512)

        val listResponse = client.get("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val mods = listResponse.body<List<InstalledMod>>()
        assertEquals(1, mods.size)
        assertEquals("Test Mod", mods[0].name)
    }

    @Test
    fun `upload duplicate mod returns 409`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val fakeJarBytes = "PKduplicate-test".toByteArray()

        client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"dup.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }

        val response = client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"dup.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `remove mod returns 204`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val fakeJarBytes = "PKremove-test".toByteArray()
        val uploadResp = client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"remove-me.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }
        val mod = uploadResp.body<InstalledMod>()

        val response = client.delete("/api/v1/instances/${instance.id}/mods/${mod.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)

        val listResp = client.get("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val mods = listResp.body<List<InstalledMod>>()
        assertTrue(mods.isEmpty())
    }

    @Test
    fun `toggle mod disable and enable`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val fakeJarBytes = "PKtoggle-test".toByteArray()
        val uploadResp = client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"toggle.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }
        val mod = uploadResp.body<InstalledMod>()
        assertTrue(mod.enabled)

        val disableResp = client.patch("/api/v1/instances/${instance.id}/mods/${mod.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ToggleModRequest(enabled = false))
        }
        assertEquals(HttpStatusCode.OK, disableResp.status)
        val disabled = disableResp.body<InstalledMod>()
        assertEquals(false, disabled.enabled)

        assertTrue(tempDir.resolve("instances/${instance.id}/mods-disabled/toggle.jar").exists())

        val enableResp = client.patch("/api/v1/instances/${instance.id}/mods/${mod.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ToggleModRequest(enabled = true))
        }
        val enabled = enableResp.body<InstalledMod>()
        assertEquals(true, enabled.enabled)

        assertTrue(tempDir.resolve("instances/${instance.id}/mods/toggle.jar").exists())
    }

    @Test
    fun `remove nonexistent mod returns 404`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.delete("/api/v1/instances/${instance.id}/mods/nonexistent") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `check updates returns empty for no mods`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.get("/api/v1/instances/${instance.id}/mods/updates") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = response.body<ModUpdatesResponse>()
        assertEquals(0, result.updatesAvailable)
        assertTrue(result.mods.isEmpty())
    }

    @Test
    fun `mod routes require auth`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/instances/fake/mods")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `toggle nonexistent mod returns 404`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.patch("/api/v1/instances/${instance.id}/mods/nonexistent") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(ToggleModRequest(enabled = false))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `install mod from modrinth`() = testApplication {
        val (tempDir) = setupTestModule(modrinthClient = createMockModrinthClient())
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.post("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(InstallModRequest(modrinthVersionId = "ver-001"))
        }
        assertEquals(HttpStatusCode.Created, response.status)

        val mod = response.body<InstalledMod>()
        assertEquals("Sodium 1.0.0", mod.name)
        assertEquals("1.0.0", mod.version)
        assertEquals("modrinth", mod.source)
        assertEquals("sodium-1.0.0.jar", mod.fileName)
        assertEquals("proj-abc", mod.modrinthProjectId)
        assertEquals("ver-001", mod.modrinthVersionId)
        assertTrue(mod.enabled)

        assertTrue(tempDir.resolve("instances/${instance.id}/mods/sodium-1.0.0.jar").exists())
    }

    @Test
    fun `update modrinth mod to new version`() = testApplication {
        val (tempDir) = setupTestModule(modrinthClient = createMockModrinthClient())
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val installResp = client.post("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(InstallModRequest(modrinthVersionId = "ver-001"))
        }
        val installed = installResp.body<InstalledMod>()

        val updateResp = client.put("/api/v1/instances/${instance.id}/mods/${installed.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateModRequest(modrinthVersionId = "ver-002"))
        }
        assertEquals(HttpStatusCode.OK, updateResp.status)

        val updated = updateResp.body<InstalledMod>()
        assertEquals("Sodium 1.1.0", updated.name)
        assertEquals("1.1.0", updated.version)
        assertEquals("sodium-1.1.0.jar", updated.fileName)
        assertEquals("ver-002", updated.modrinthVersionId)
        assertEquals("proj-abc", updated.modrinthProjectId)

        assertTrue(tempDir.resolve("instances/${instance.id}/mods/sodium-1.1.0.jar").exists())
        assertTrue(!tempDir.resolve("instances/${instance.id}/mods/sodium-1.0.0.jar").exists())
    }

    @Test
    fun `check updates finds newer version`() = testApplication {
        val (tempDir) = setupTestModule(modrinthClient = createMockModrinthClient())
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        client.post("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(InstallModRequest(modrinthVersionId = "ver-001"))
        }

        val response = client.get("/api/v1/instances/${instance.id}/mods/updates") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val result = response.body<ModUpdatesResponse>()
        assertEquals(1, result.updatesAvailable)
        assertEquals("ver-002", result.mods[0].latestVersionId)
        assertEquals("1.1.0", result.mods[0].latestVersion)
    }

    @Test
    fun `upload non-jar file is rejected`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val response = client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", "not a jar".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"bad.txt\"")
                    append(HttpHeaders.ContentType, "text/plain")
                })
            }))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `upload mod with path traversal filename is sanitized`() = testApplication {
        val (tempDir) = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token, tempDir = tempDir)

        val fakeJarBytes = "PKtraversal-test-content".toByteArray()
        val response = client.post("/api/v1/instances/${instance.id}/mods/upload") {
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody(MultiPartFormDataContent(formData {
                append("file", fakeJarBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"../../../evil.jar\"")
                    append(HttpHeaders.ContentType, "application/java-archive")
                })
            }))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val mod = response.body<InstalledMod>()
        assertEquals("evil.jar", mod.fileName)
    }
}
