package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigRoutesTest {

    // --- Daemon Config ---

    @Test
    fun `get daemon config returns defaults`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val config = response.body<DaemonConfig>()
        assertEquals(9147, config.port)
        assertTrue(config.publicEndpointEnabled)
        assertEquals(listOf("-Xmx4G", "-Xms2G"), config.defaultJvmArgs)
    }

    @Test
    fun `update daemon config partial update`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.put("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateDaemonConfigRequest(defaultJvmArgs = listOf("-Xmx8G")))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val config = response.body<DaemonConfig>()
        assertEquals(9147, config.port)
        assertEquals(listOf("-Xmx8G"), config.defaultJvmArgs)

        val getResponse = client.get("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val reloaded = getResponse.body<DaemonConfig>()
        assertEquals(listOf("-Xmx8G"), reloaded.defaultJvmArgs)
    }

    @Test
    fun `daemon config returns default download source`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val config = response.body<DaemonConfig>()
        assertEquals(DownloadSource.MOJANG, config.downloadSource)
        assertEquals(null, config.customMirrorUrl)
    }

    @Test
    fun `update download source to bmclapi`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.put("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateDaemonConfigRequest(downloadSource = DownloadSource.BMCLAPI))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val config = response.body<DaemonConfig>()
        assertEquals(DownloadSource.BMCLAPI, config.downloadSource)
        assertEquals(9147, config.port)

        val reloaded = client.get("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<DaemonConfig>()
        assertEquals(DownloadSource.BMCLAPI, reloaded.downloadSource)
    }

    @Test
    fun `update download source to custom with mirror url`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.put("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateDaemonConfigRequest(
                downloadSource = DownloadSource.CUSTOM,
                customMirrorUrl = "https://my-mirror.example.com",
            ))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val config = response.body<DaemonConfig>()
        assertEquals(DownloadSource.CUSTOM, config.downloadSource)
        assertEquals("https://my-mirror.example.com", config.customMirrorUrl)
    }

    @Test
    fun `daemon config requires auth`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/config/daemon")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- JVM Config ---

    @Test
    fun `get jvm config returns nulls for new instance`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val jvmConfig = response.body<JvmConfig>()
        assertEquals(null, jvmConfig.jvmArgs)
        assertEquals(null, jvmConfig.javaPath)
        assertEquals("java", jvmConfig.effectiveJavaPath)
    }

    @Test
    fun `get jvm config with instance-level jvmArgs from creation`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val instance = client.post("/api/v1/instances") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(CreateInstanceRequest(name = "Custom JVM", mcVersion = "1.20.4", jvmArgs = listOf("-Xmx8G")))
        }.body<InstanceSummary>()

        val response = client.get("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val jvmConfig = response.body<JvmConfig>()
        assertEquals(listOf("-Xmx8G"), jvmConfig.jvmArgs)
    }

    @Test
    fun `update jvm config sets jvmArgs`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.put("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"jvmArgs": ["-Xmx16G", "-Xms4G"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val jvmConfig = response.body<JvmConfig>()
        assertEquals(listOf("-Xmx16G", "-Xms4G"), jvmConfig.jvmArgs)
        assertEquals(null, jvmConfig.javaPath)
    }

    @Test
    fun `update jvm config sets javaPath`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.put("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"javaPath": "/usr/lib/jvm/java-21/bin/java"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val jvmConfig = response.body<JvmConfig>()
        assertEquals("/usr/lib/jvm/java-21/bin/java", jvmConfig.javaPath)
        assertEquals("/usr/lib/jvm/java-21/bin/java", jvmConfig.effectiveJavaPath)
    }

    @Test
    fun `update jvm config reset javaPath to null reverts to default`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        client.put("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"javaPath": "/custom/java"}""")
        }

        val response = client.put("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"javaPath": null}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val jvmConfig = response.body<JvmConfig>()
        assertEquals(null, jvmConfig.javaPath)
        assertEquals("java", jvmConfig.effectiveJavaPath)
    }

    @Test
    fun `update jvm config partial update preserves other field`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        client.put("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"jvmArgs": ["-Xmx4G"], "javaPath": "/my/java"}""")
        }

        val response = client.put("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"jvmArgs": ["-Xmx8G"]}""")
        }
        val jvmConfig = response.body<JvmConfig>()
        assertEquals(listOf("-Xmx8G"), jvmConfig.jvmArgs)
        assertEquals("/my/java", jvmConfig.javaPath)
    }

    @Test
    fun `jvm config 404 for nonexistent instance`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/instances/nonexistent/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Invite ---

    @Test
    fun `get invite returns url and enabled by default`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.get("/api/v1/instances/${instance.id}/invite") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val invite = response.body<InviteInfo>()
        assertTrue(invite.publicEndpointEnabled)
        assertTrue(invite.url.contains(instance.id))
        assertTrue(invite.url.startsWith("conduit://"))
    }

    @Test
    fun `disable public endpoint via invite`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)
        val instance = createTestInstance(client, token)

        val response = client.put("/api/v1/instances/${instance.id}/invite") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateInviteRequest(publicEndpointEnabled = false))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val invite = response.body<InviteInfo>()
        assertEquals(false, invite.publicEndpointEnabled)

        val getResponse = client.get("/api/v1/instances/${instance.id}/invite") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val reloaded = getResponse.body<InviteInfo>()
        assertEquals(false, reloaded.publicEndpointEnabled)
    }

    @Test
    fun `invite 404 for nonexistent instance`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/instances/nonexistent/invite") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
