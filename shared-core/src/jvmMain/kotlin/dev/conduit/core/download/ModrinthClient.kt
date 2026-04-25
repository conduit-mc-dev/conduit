package dev.conduit.core.download

import dev.conduit.core.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream

class ModrinthClient : Closeable {

    companion object {
        private const val BASE_URL = "https://api.modrinth.com/v2"
        private const val USER_AGENT = "Conduit-MC/0.1.0 (https://github.com/conduit-mc-dev/conduit)"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(this@ModrinthClient.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
        }
        defaultRequest { header("User-Agent", USER_AGENT) }
        expectSuccess = false
    }

    suspend fun search(
        query: String,
        mcVersion: String? = null,
        loader: String? = null,
        offset: Int = 0,
        limit: Int = 20,
    ): ModrinthSearchResponse {
        val facets = buildFacets(mcVersion, loader)
        val response = client.get("$BASE_URL/search") {
            parameter("query", query)
            parameter("offset", offset)
            parameter("limit", limit.coerceIn(1, 100))
            if (facets.isNotEmpty()) parameter("facets", facets)
        }
        checkResponse(response)
        val raw = response.body<ModrinthRawSearchResponse>()
        return ModrinthSearchResponse(
            hits = raw.hits.map { it.toSearchHit() },
            totalHits = raw.totalHits,
            offset = raw.offset,
            limit = raw.limit,
        )
    }

    suspend fun getProjectVersions(
        projectId: String,
        mcVersion: String? = null,
        loader: String? = null,
    ): List<ModrinthVersionInfo> {
        val response = client.get("$BASE_URL/project/$projectId/version") {
            mcVersion?.let { parameter("game_versions", """["$it"]""") }
            loader?.let { parameter("loaders", """["$it"]""") }
        }
        checkResponse(response)
        val raw = response.body<List<ModrinthRawVersion>>()
        return raw.map { it.toVersionInfo() }
    }

    suspend fun getVersion(versionId: String): ModrinthVersionInfo {
        val response = client.get("$BASE_URL/version/$versionId")
        checkResponse(response)
        return response.body<ModrinthRawVersion>().toVersionInfo()
    }

    suspend fun downloadFile(url: String, destination: Path): Long {
        destination.parent?.createDirectories()
        var bytesWritten = 0L
        client.prepareGet(url) {
            timeout { requestTimeoutMillis = 600_000 }
        }.execute { response ->
            checkResponse(response)
            response.bodyAsChannel().toInputStream().use { input ->
                destination.outputStream().buffered().use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        bytesWritten += read
                    }
                }
            }
        }
        return bytesWritten
    }

    private fun buildFacets(mcVersion: String?, loader: String?): String {
        val parts = mutableListOf<String>()
        parts.add("""["project_type:mod"]""")
        mcVersion?.let { parts.add("""["versions:$it"]""") }
        loader?.let { parts.add("""["categories:$it"]""") }
        return "[${parts.joinToString(",")}]"
    }

    private suspend fun checkResponse(response: io.ktor.client.statement.HttpResponse) {
        if (response.status.value >= 400) {
            val body = try { response.bodyAsText() } catch (_: Exception) { "" }
            throw ModrinthApiException(response.status.value, body)
        }
    }

    override fun close() {
        client.close()
    }
}

class ModrinthApiException(val statusCode: Int, val body: String) :
    RuntimeException("Modrinth API error $statusCode: $body")

// --- Modrinth raw API response mapping ---

@Serializable
private data class ModrinthRawSearchResponse(
    val hits: List<ModrinthRawSearchHit>,
    val total_hits: Int = 0,
    val offset: Int = 0,
    val limit: Int = 20,
) {
    val totalHits get() = total_hits
}

@Serializable
private data class ModrinthRawSearchHit(
    val project_id: String,
    val slug: String,
    val title: String,
    val description: String,
    val author: String,
    val icon_url: String? = null,
    val downloads: Long = 0,
    val latest_version: String? = null,
    val categories: List<String> = emptyList(),
    val client_side: String? = null,
    val server_side: String? = null,
) {
    fun toSearchHit() = ModrinthSearchHit(
        projectId = project_id,
        slug = slug,
        title = title,
        description = description,
        author = author,
        iconUrl = icon_url,
        downloads = downloads,
        latestVersion = latest_version,
        categories = categories,
        env = ModEnvSupport(client = client_side, server = server_side),
    )
}

@Serializable
private data class ModrinthRawVersion(
    val id: String,
    val version_number: String,
    val name: String,
    val changelog: String? = null,
    val game_versions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val date_published: String = "",
    val files: List<ModrinthRawFile> = emptyList(),
    val dependencies: List<ModrinthRawDependency> = emptyList(),
) {
    fun toVersionInfo() = ModrinthVersionInfo(
        versionId = id,
        versionNumber = version_number,
        name = name,
        changelog = changelog,
        gameVersions = game_versions,
        loaders = loaders,
        datePublished = date_published,
        files = files.map { it.toVersionFile() },
        dependencies = dependencies.map { it.toDependency() },
    )
}

@Serializable
private data class ModrinthRawFile(
    val filename: String = "",
    val url: String? = null,
    val size: Long = 0,
    val hashes: Map<String, String> = emptyMap(),
) {
    fun toVersionFile() = ModrinthVersionFile(
        fileName = filename,
        fileSize = size,
        url = url,
        hashes = ModrinthFileHashes(sha1 = hashes["sha1"], sha512 = hashes["sha512"]),
    )
}

@Serializable
private data class ModrinthRawDependency(
    val project_id: String? = null,
    val dependency_type: String = "required",
) {
    fun toDependency() = ModrinthDependency(
        projectId = project_id,
        dependencyType = dependency_type,
    )
}
