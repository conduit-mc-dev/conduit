package dev.conduit.daemon.service

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.TaskStore
import org.slf4j.LoggerFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import kotlin.io.path.*

class LoaderService(
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val taskStore: TaskStore,
    private val scope: CoroutineScope,
    httpClient: HttpClient? = null,
) : Closeable {

    private val log = LoggerFactory.getLogger(LoaderService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = httpClient ?: HttpClient(CIO) {
        install(ContentNegotiation) { json(this@LoaderService.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
        }
        expectSuccess = false
    }

    fun getCurrentLoader(instanceId: String): LoaderInfo? =
        instanceStore.getLoader(instanceId)

    suspend fun getAvailableLoaders(mcVersion: String): List<AvailableLoader> {
        val loaders = mutableListOf<AvailableLoader>()

        try {
            val fabricVersions = fetchFabricVersions(mcVersion)
            if (fabricVersions.isNotEmpty()) {
                loaders.add(AvailableLoader(type = LoaderType.FABRIC, versions = fabricVersions))
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch Fabric versions for MC {}", mcVersion, e)
        }

        try {
            val quiltVersions = fetchQuiltVersions(mcVersion)
            if (quiltVersions.isNotEmpty()) {
                loaders.add(AvailableLoader(type = LoaderType.QUILT, versions = quiltVersions))
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch Quilt versions for MC {}", mcVersion, e)
        }

        return loaders
    }

    fun install(instanceId: String, type: LoaderType, version: String): String {
        val instance = instanceStore.get(instanceId)
        if (instance.state != InstanceState.STOPPED) {
            throw ApiException(HttpStatusCode.Conflict, "SERVER_MUST_BE_STOPPED", "Server must be stopped to install a loader")
        }
        if (instance.loader != null) {
            throw ApiException(HttpStatusCode.Conflict, "LOADER_ALREADY_INSTALLED", "Uninstall the current loader first")
        }

        val taskId = taskStore.create(instanceId, TaskStore.TYPE_LOADER_INSTALL, "Installing ${type.name} $version...")
        scope.launch {
            try {
                taskStore.updateProgress(taskId, 0.1, "Downloading installer...")
                installLoader(instanceId, instance.mcVersion, type, version)
                taskStore.updateProgress(taskId, 0.9, "Updating metadata...")
                instanceStore.setLoader(instanceId, LoaderInfo(type = type, version = version, mcVersion = instance.mcVersion))
                taskStore.complete(taskId, success = true, "Loader installed successfully")
            } catch (e: Exception) {
                taskStore.complete(taskId, success = false, "Installation failed: ${e.message}")
            }
        }
        return taskId
    }

    fun uninstall(instanceId: String) {
        val instance = instanceStore.get(instanceId)
        if (instance.state != InstanceState.STOPPED) {
            throw ApiException(HttpStatusCode.Conflict, "SERVER_MUST_BE_STOPPED", "Server must be stopped to uninstall a loader")
        }
        if (instance.loader == null) {
            throw ApiException(HttpStatusCode.NotFound, "NO_LOADER_INSTALLED", "No loader is installed")
        }
        instanceStore.setLoader(instanceId, null)
    }

    private suspend fun installLoader(instanceId: String, mcVersion: String, type: LoaderType, version: String) {
        when (type) {
            LoaderType.FABRIC -> installFabric(instanceId, mcVersion, version)
            LoaderType.QUILT -> installQuilt(instanceId, mcVersion, version)
            LoaderType.FORGE, LoaderType.NEOFORGE ->
                throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "${type.name} installation not yet supported")
        }
    }

    private suspend fun installFabric(instanceId: String, mcVersion: String, loaderVersion: String) {
        val installerVersions = client.get("https://meta.fabricmc.net/v2/versions/installer")
            .bodyAsText().let { json.decodeFromString<List<FabricInstallerVersion>>(it) }
        val installerVersion = installerVersions.firstOrNull { it.stable }?.version
            ?: installerVersions.firstOrNull()?.version
            ?: throw RuntimeException("No Fabric installer version found")

        val serverJarUrl = "https://meta.fabricmc.net/v2/versions/loader/$mcVersion/$loaderVersion/$installerVersion/server/jar"
        val response = client.get(serverJarUrl)
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to download Fabric server jar: ${response.status}")
        }
        val bytes = response.bodyAsBytes()
        val destination = dataDirectory.serverJarPath(instanceId)
        destination.writeBytes(bytes)
    }

    private suspend fun installQuilt(instanceId: String, mcVersion: String, loaderVersion: String) {
        val serverJarUrl = "https://meta.quiltmc.org/v3/versions/loader/$mcVersion/$loaderVersion/server/jar"
        val response = client.get(serverJarUrl)
        if (response.status != HttpStatusCode.OK) {
            throw RuntimeException("Failed to download Quilt server jar: ${response.status}")
        }
        val bytes = response.bodyAsBytes()
        val destination = dataDirectory.serverJarPath(instanceId)
        destination.writeBytes(bytes)
    }

    private suspend fun fetchFabricVersions(mcVersion: String): List<String> {
        val response = client.get("https://meta.fabricmc.net/v2/versions/loader/$mcVersion")
        if (response.status != HttpStatusCode.OK) return emptyList()
        val versions = json.decodeFromString<List<FabricLoaderEntry>>(response.bodyAsText())
        return versions.map { it.loader.version }
    }

    private suspend fun fetchQuiltVersions(mcVersion: String): List<String> {
        val response = client.get("https://meta.quiltmc.org/v3/versions/loader/$mcVersion")
        if (response.status != HttpStatusCode.OK) return emptyList()
        val versions = json.decodeFromString<List<QuiltLoaderEntry>>(response.bodyAsText())
        return versions.map { it.loader.version }
    }

    override fun close() {
        client.close()
    }
}

@Serializable
private data class FabricInstallerVersion(val version: String, val stable: Boolean = false)

@Serializable
private data class FabricLoaderEntry(val loader: FabricLoaderInfo)

@Serializable
private data class FabricLoaderInfo(val version: String)

@Serializable
private data class QuiltLoaderEntry(val loader: QuiltLoaderInfo)

@Serializable
private data class QuiltLoaderInfo(val version: String)
