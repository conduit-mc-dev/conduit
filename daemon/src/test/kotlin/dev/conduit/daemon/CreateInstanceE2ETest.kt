package dev.conduit.daemon

import dev.conduit.core.api.ConduitApiClient
import dev.conduit.core.model.*
import dev.conduit.daemon.service.DataDirectory
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.io.path.*
import kotlin.test.*

/**
 * E2E data flow test for CreateInstanceScreen.
 *
 * Starts a REAL daemon (embedded Netty) with mock external APIs (Mojang, Fabric/Forge),
 * then uses the real ConduitApiClient to verify the full data pipeline:
 *   Daemon API → ConduitApiClient → data available for UI rendering
 */
class CreateInstanceE2ETest {

    @Test
    fun `create instance data flow end to end`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val tempDir = createTempDirectory("conduit-e2e")
        tempDir.toFile().deleteOnExit()

        val server = embeddedServer(Netty, port = port) {
            module(
                dataDirectory = DataDirectory(tempDir),
                mojangClient = createMockMojangClient(),
                loaderHttpClient = createMockLoaderHttpClient(),
            )
        }
        server.start(wait = false)

        try {
            val apiClient = ConduitApiClient(baseUrl = "http://localhost:$port")

            // Step 1: Pair
            val code = apiClient.initiatePairing().code
            assertTrue(code.isNotBlank(), "Pairing code should not be blank")
            val confirmResponse = apiClient.confirmPairing(code, "E2E Test Desktop")
            apiClient.setToken(confirmResponse.token)

            // Step 2: Fetch MC versions
            val versionsResponse = apiClient.listMinecraftVersions()
            assertTrue(versionsResponse.versions.isNotEmpty(), "MC versions should not be empty")
            val latestRelease = versionsResponse.versions.firstOrNull { it.type == "release" }
            assertNotNull(latestRelease, "Should have at least one release version")
            println("Latest MC version: ${latestRelease.id}")

            // Step 3: Fetch available loaders
            val loaders = apiClient.listAvailableLoadersByMcVersion(latestRelease.id)
            assertTrue(loaders.isNotEmpty(), "Available loaders should not be empty")
            loaders.forEach { loader ->
                assertTrue(loader.versions.isNotEmpty(), "${loader.type} should have versions")
                println("  ${loader.type}: ${loader.versions.size} versions (latest: ${loader.versions.first()})")
            }

            // Step 4: Create instance
            val instance = apiClient.createInstance(
                CreateInstanceRequest(
                    name = "E2E Test Server",
                    mcVersion = latestRelease.id,
                    mcPort = 25599,
                    maxPlayers = 10,
                )
            )
            assertEquals("E2E Test Server", instance.name)
            assertEquals(latestRelease.id, instance.mcVersion)
            assertEquals(25599, instance.mcPort)
            assertEquals(10, instance.maxPlayers)
            println("Created instance: ${instance.id}")

            // Step 5: Verify instance-scoped loaders match standalone
            val instanceLoaders = apiClient.listAvailableLoaders(instance.id)
            assertEquals(loaders.size, instanceLoaders.size)

            // Step 6: Verify server.properties has max-players
            val props = apiClient.getServerProperties(instance.id)
            assertEquals("10", props["max-players"])

            // Step 7: Install loader
            val fabricLoader = loaders.find { it.type == LoaderType.FABRIC }
            if (fabricLoader != null) {
                val task = apiClient.installLoader(
                    instance.id,
                    InstallLoaderRequest(type = LoaderType.FABRIC, version = fabricLoader.versions.first()),
                )
                assertTrue(task.taskId.isNotBlank())
                println("Loader install task: ${task.taskId}")
            }

            println("\n✅ E2E data flow verified: versions → loaders → create → properties → loader install")
        } finally {
            server.stop(100, 500)
        }
    }

    @Test
    fun `loader versions consistent between endpoints`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val tempDir = createTempDirectory("conduit-e2e-loaders")
        tempDir.toFile().deleteOnExit()

        val server = embeddedServer(Netty, port = port) {
            module(
                dataDirectory = DataDirectory(tempDir),
                mojangClient = createMockMojangClient(),
                loaderHttpClient = createMockLoaderHttpClient(),
            )
        }
        server.start(wait = false)

        try {
            val apiClient = ConduitApiClient(baseUrl = "http://localhost:$port")
            val code = apiClient.initiatePairing().code
            apiClient.setToken(apiClient.confirmPairing(code, "Test").token)

            val mcVersion = apiClient.listMinecraftVersions().versions.first { it.type == "release" }.id

            // Standalone endpoint
            val standalone = apiClient.listAvailableLoadersByMcVersion(mcVersion)

            // Instance-scoped endpoint
            val instance = apiClient.createInstance(
                CreateInstanceRequest(name = "Loader Test", mcVersion = mcVersion)
            )
            val instanceScoped = apiClient.listAvailableLoaders(instance.id)

            assertEquals(standalone.size, instanceScoped.size)
            for (i in standalone.indices) {
                assertEquals(standalone[i].type, instanceScoped[i].type)
                assertEquals(standalone[i].versions, instanceScoped[i].versions)
            }
            println("✅ Loader versions consistent: ${standalone.size} loaders match across endpoints")
        } finally {
            server.stop(100, 500)
        }
    }
}
