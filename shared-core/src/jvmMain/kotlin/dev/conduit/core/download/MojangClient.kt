package dev.conduit.core.download

import dev.conduit.core.model.MinecraftVersion
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
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

class MojangClient : Closeable {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        expectSuccess = true
    }

    @Volatile
    private var cachedManifest: MojangVersionManifest? = null

    suspend fun fetchManifest(forceRefresh: Boolean = false): MojangVersionManifest {
        if (!forceRefresh) cachedManifest?.let { return it }
        val manifest: MojangVersionManifest = client.get(MOJANG_VERSION_MANIFEST_URL).body()
        cachedManifest = manifest
        return manifest
    }

    suspend fun listReleases(forceRefresh: Boolean = false): List<MinecraftVersion> {
        val manifest = fetchManifest(forceRefresh)
        return manifest.versions
            .filter { it.type == "release" }
            .map { MinecraftVersion(id = it.id, type = it.type, releaseTime = it.releaseTime) }
    }

    suspend fun fetchVersionDetail(versionUrl: String): MojangVersionDetail =
        client.get(versionUrl).body()

    // SHA-1 校验失败时删除残留文件并抛异常
    suspend fun downloadServerJar(mcVersion: String, destination: Path): Long {
        val manifest = fetchManifest()
        val entry = manifest.versions.firstOrNull { it.id == mcVersion }
            ?: error("Minecraft version $mcVersion not found in manifest")

        val detail = fetchVersionDetail(entry.url)
        val serverDownload = detail.downloads.server
            ?: error("Minecraft version $mcVersion has no server download")

        destination.parent?.createDirectories()

        val digest = MessageDigest.getInstance("SHA-1")
        var bytesWritten = 0L

        client.prepareGet(serverDownload.url).execute { response ->
            response.bodyAsChannel().toInputStream().use { input ->
                destination.outputStream().buffered().use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesWritten += read
                    }
                }
            }
        }

        val actualSha1 = digest.digest().joinToString("") { "%02x".format(it) }
        if (actualSha1 != serverDownload.sha1) {
            destination.deleteIfExists()
            error("SHA-1 mismatch for server.jar: expected ${serverDownload.sha1}, got $actualSha1")
        }

        return bytesWritten
    }

    override fun close() {
        client.close()
    }
}
