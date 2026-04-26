package dev.conduit.core.download

import dev.conduit.core.model.DownloadSource
import dev.conduit.core.model.MinecraftVersion
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

class MojangClient(
    private val downloadSourceProvider: () -> Pair<DownloadSource, String?> = { Pair(DownloadSource.MOJANG, null) },
    httpClient: HttpClient? = null,
) : Closeable {

    private val client = httpClient ?: HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 30_000
        }
        defaultRequest {
            header("User-Agent", "Conduit-MC/0.1.0 (https://github.com/conduit-mc-dev/conduit)")
        }
        expectSuccess = true
    }

    @Volatile
    private var cachedManifest: MojangVersionManifest? = null

    @Volatile
    var cachedAt: kotlin.time.Instant? = null
        private set

    suspend fun fetchManifest(forceRefresh: Boolean = false): MojangVersionManifest {
        if (!forceRefresh) cachedManifest?.let { return it }
        val manifest: MojangVersionManifest = client.get(mirrorUrl(MOJANG_VERSION_MANIFEST_URL)).body()
        cachedManifest = manifest
        cachedAt = kotlin.time.Clock.System.now()
        return manifest
    }

    suspend fun listVersions(type: String = "release", forceRefresh: Boolean = false): List<MinecraftVersion> {
        val manifest = fetchManifest(forceRefresh)
        return manifest.versions
            .let { versions ->
                when (type) {
                    "release" -> versions.filter { it.type == "release" }
                    "snapshot" -> versions.filter { it.type == "snapshot" }
                    else -> versions
                }
            }
            .map { MinecraftVersion(id = it.id, type = it.type, releaseTime = it.releaseTime) }
    }

    suspend fun fetchVersionDetail(versionUrl: String): MojangVersionDetail =
        client.get(mirrorUrl(versionUrl)).body()

    suspend fun downloadServerJar(
        mcVersion: String,
        destination: Path,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): Long {
        val manifest = fetchManifest()
        val entry = manifest.versions.firstOrNull { it.id == mcVersion }
            ?: error("Minecraft version $mcVersion not found in manifest")

        val detail = fetchVersionDetail(entry.url)
        val serverDownload = detail.downloads.server
            ?: error("Minecraft version $mcVersion has no server download")

        val url = mirrorUrl(serverDownload.url)

        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                return doDownload(url, destination, serverDownload.sha1, serverDownload.size, onProgress)
            } catch (e: ClientRequestException) {
                if (e.response.status.value in 400..499) throw e
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }
            if (attempt < 2) {
                destination.deleteIfExists()
                kotlinx.coroutines.delay(1000)
            }
        }
        throw lastException!!
    }

    private suspend fun doDownload(
        url: String,
        destination: Path,
        expectedSha1: String,
        expectedSize: Long,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)?,
    ): Long {
        destination.parent?.createDirectories()

        val digest = MessageDigest.getInstance("SHA-1")
        var bytesWritten = 0L

        client.prepareGet(url) {
            timeout {
                requestTimeoutMillis = 600_000
                socketTimeoutMillis = 120_000
            }
        }.execute { response ->
            response.bodyAsChannel().toInputStream().use { input ->
                destination.outputStream().buffered().use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesWritten += read
                        onProgress?.invoke(bytesWritten, expectedSize)
                    }
                }
            }
        }

        val actualSha1 = digest.digest().joinToString("") { "%02x".format(it) }
        if (actualSha1 != expectedSha1) {
            destination.deleteIfExists()
            error("SHA-1 mismatch for server.jar: expected $expectedSha1, got $actualSha1")
        }

        return bytesWritten
    }

    private fun mirrorUrl(originalUrl: String): String {
        val (source, customUrl) = downloadSourceProvider()
        return transformUrl(originalUrl, source, customUrl)
    }

    override fun close() {
        client.close()
    }

    companion object {
        private val MOJANG_DOMAINS = listOf(
            "https://piston-data.mojang.com",
            "https://launchermeta.mojang.com",
            "https://piston-meta.mojang.com",
        )

        private fun transformUrl(originalUrl: String, source: DownloadSource, customMirrorUrl: String? = null): String =
            when (source) {
                DownloadSource.MOJANG -> originalUrl
                DownloadSource.BMCLAPI -> {
                    var result = originalUrl
                    for (domain in MOJANG_DOMAINS) {
                        result = result.replace(domain, "https://bmclapi2.bangbang93.com")
                    }
                    result
                }
                DownloadSource.CUSTOM -> {
                    val base = customMirrorUrl?.trimEnd('/') ?: return originalUrl
                    var result = originalUrl
                    for (domain in MOJANG_DOMAINS) {
                        result = result.replace(domain, base)
                    }
                    result
                }
            }
    }
}
