package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    private lateinit var tempDir: Path

    private fun testModule(): TestApplicationBuilder.() -> Unit = {
        application {
            tempDir = Files.createTempDirectory("conduit-test")
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

    private suspend fun createTestInstance(
        client: io.ktor.client.HttpClient,
        token: String,
    ): InstanceSummary {
        val instance = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Test Server", mcVersion = "1.20.4"))
        }.body<InstanceSummary>()
        tempDir.resolve("instances").resolve(instance.id).createDirectories()
        return instance
    }

    @Test
    fun `list mods returns empty for new instance`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/mods") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val mods = response.body<List<InstalledMod>>()
        assertTrue(mods.isEmpty())
    }

    @Test
    fun `upload custom mod and list`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val fakeJarBytes = "PKfake-jar-content-for-testing".toByteArray()

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
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val fakeJarBytes = "PKduplicate-test".toByteArray()

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
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val fakeJarBytes = "PKremove-test".toByteArray()
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
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val fakeJarBytes = "PKtoggle-test".toByteArray()
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
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.delete("/api/v1/instances/${instance.id}/mods/nonexistent") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `check updates returns empty for no mods`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

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
        testModule()()
        val client = jsonClient()

        val response = client.get("/api/v1/instances/fake/mods")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `upload non-jar file is rejected`() = testApplication {
        testModule()()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

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
}
