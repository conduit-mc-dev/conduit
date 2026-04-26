package dev.conduit.daemon

import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.DaemonConfigStore
import dev.conduit.daemon.store.InstanceStore
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.path.readText
import kotlin.test.*

class PersistenceTest {

    @Test
    fun `daemon config persists across store recreation`() = withTempDir { dir ->
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()

        testApplication {
            application { module(dataDirectory = dataDir, instanceStore = InstanceStore(dataDir)) }
            val client = jsonClient()
            val token = pairAndGetToken(client)

            client.put("/api/v1/config/daemon") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(UpdateDaemonConfigRequest(
                    defaultJvmArgs = listOf("-Xmx8G", "-Xms4G"),
                    downloadSource = DownloadSource.BMCLAPI,
                ))
            }
        }

        val reloadedStore = DaemonConfigStore(dataDir.configPath)
        val config = reloadedStore.get()
        assertEquals(listOf("-Xmx8G", "-Xms4G"), config.defaultJvmArgs)
        assertEquals(DownloadSource.BMCLAPI, config.downloadSource)
    }

    @Test
    fun `EULA acceptance persists to disk`() = withTempDir { dir ->
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()

        testApplication {
            application { module(dataDirectory = dataDir, instanceStore = InstanceStore(dataDir)) }
            val client = jsonClient()
            val token = pairAndGetToken(client)
            val instance = createTestInstance(client, token, tempDir = dir)

            client.put("/api/v1/instances/${instance.id}/server/eula") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(AcceptEulaRequest(accepted = true))
            }

            val eulaFile = dataDir.instanceDir(instance.id).resolve("eula.txt")
            val content = eulaFile.readText()
            assertTrue(content.contains("eula=true"), "eula.txt should contain 'eula=true', got: $content")
        }
    }
}
