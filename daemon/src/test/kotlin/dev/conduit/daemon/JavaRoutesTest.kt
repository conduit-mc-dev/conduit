package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.testutil.forceInitializing
import dev.conduit.daemon.testutil.setupTestModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaRoutesTest {

    @Test
    fun `list java installations returns non-empty list`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.get("/api/v1/java/installations") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val installations = response.body<List<JavaInstallation>>()
        assertTrue(installations.isNotEmpty(), "Should detect at least one Java installation")
        assertTrue(installations.any { it.version.isNotBlank() })
    }

    @Test
    fun `set default java with invalid path returns 422`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val response = client.put("/api/v1/java/default") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SetDefaultJavaRequest(path = "/nonexistent/java"))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun `java routes require auth`() = testApplication {
        setupTestModule()
        val client = jsonClient()

        val response = client.get("/api/v1/java/installations")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `set default Java persists to daemon config`() = testApplication {
        setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        val installations = client.get("/api/v1/java/installations") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<JavaInstallation>>()
        val defaultPath = installations.first().path

        val response = client.put("/api/v1/java/default") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(SetDefaultJavaRequest(path = defaultPath))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val config = client.get("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<DaemonConfig>()
        assertEquals(defaultPath, config.defaultJavaPath)
    }

    @Test
    fun `JVM config effectiveJavaPath falls back to daemon default`() = testApplication {
        val env = setupTestModule()
        val client = jsonClient()
        val token = pairAndGetToken(client)

        client.put("/api/v1/config/daemon") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateDaemonConfigRequest(defaultJavaPath = "/custom/daemon/java"))
        }

        val instance = createTestInstance(client, token)
        env.forceInitializing(instance.id)

        val response = client.get("/api/v1/instances/${instance.id}/config/jvm") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val jvmConfig = response.body<JvmConfig>()
        assertEquals("/custom/daemon/java", jvmConfig.effectiveJavaPath)
        assertNull(jvmConfig.javaPath)
    }
}
