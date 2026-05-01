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
import kotlin.time.Duration.Companion.hours
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

/**
 * Compares two Minecraft version strings in descending order (newest first).
 *
 * Handles release versions (1.21.5), snapshots (25w14a), pre-releases (1.21-pre1),
 * and release candidates (1.21-rc1).
 *
 * @return negative if [a] is newer than [b], zero if equal, positive if [b] is newer than [a].
 */
fun compareMcVersions(a: String, b: String): Int {
    val aParts = a.split(".", "-")
    val bParts = b.split(".", "-")
    val minLen = minOf(aParts.size, bParts.size)
    for (i in 0 until minLen) {
        val aInt = aParts[i].toIntOrNull()
        val bInt = bParts[i].toIntOrNull()
        if (aInt != null && bInt != null) {
            // Numeric comparison, descending
            val diff = bInt - aInt
            if (diff != 0) return diff
        } else if (aInt != null) {
            // a segment is numeric, b segment is not — numeric is newer
            return -1
        } else if (bInt != null) {
            // b segment is numeric, a segment is not — numeric is newer
            return 1
        } else {
            // Lexicographic comparison, descending
            val cmp = bParts[i].compareTo(aParts[i])
            if (cmp != 0) return cmp
        }
    }
    // One is a prefix of the other.
    if (aParts.size == bParts.size) return 0
    val nextSegment = (if (aParts.size > bParts.size) aParts else bParts)[minLen]
    // If the extra segment starts with a letter (pre-release suffix like "pre1", "rc1"),
    // the shorter version (without the suffix) is considered newer.
    if (nextSegment.firstOrNull()?.isLetter() == true) {
        return if (aParts.size > bParts.size) 1 else -1
    }
    // Otherwise (numeric extra segment), the longer version is newer.
    return bParts.size - aParts.size
}

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
        if (!forceRefresh) {
            val cached = cachedManifest
            val age = cachedAt
            if (cached != null && age != null && (kotlin.time.Clock.System.now() - age) < MANIFEST_TTL) {
                return cached
            }
        }
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
            .sortedWith(
                compareBy<MojangVersionEntry> { it.type != "release" }
                    .thenComparator { a, b -> compareMcVersions(a.id, b.id) },
            )
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
        repeat(MAX_RETRIES) { attempt ->
            try {
                return doDownload(url, destination, serverDownload.sha1, serverDownload.size, onProgress)
            } catch (e: ClientRequestException) {
                if (e.response.status.value in 400..499) throw e
                lastException = e
            } catch (e: Exception) {
                lastException = e
            }
            if (attempt < MAX_RETRIES - 1) {
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
        private const val MAX_RETRIES = 3
        private val MANIFEST_TTL = 1.hours

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
