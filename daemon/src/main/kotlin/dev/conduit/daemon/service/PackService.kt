package dev.conduit.daemon.service

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.ModStore
import dev.conduit.daemon.store.PackStore
import dev.conduit.daemon.store.TaskStore
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

class PackService(
    private val modStore: ModStore,
    private val instanceStore: InstanceStore,
    private val packStore: PackStore,
    private val dataDirectory: DataDirectory,
    private val taskStore: TaskStore,
    private val scope: CoroutineScope,
    private val json: Json,
) {
    fun getPackInfo(instanceId: String): PackInfo {
        val pack = packStore.get(instanceId)
            ?: throw ApiException(HttpStatusCode.NotFound, "PACK_NOT_BUILT", "No pack has been built yet")
        val mods = modStore.list(instanceId)
        return PackInfo(
            name = instanceStore.get(instanceId).name,
            versionId = pack.versionId,
            lastBuiltAt = pack.lastBuiltAt,
            dirty = pack.dirty,
            fileSize = pack.fileSize,
            modCount = mods.size,
            hash = pack.sha256?.let { PackHash(sha256 = it) },
        )
    }

    fun build(instanceId: String, versionId: String?, summary: String?): String {
        val existing = packStore.getOrCreate(instanceId)
        if (existing.buildState == "building") {
            throw ApiException(HttpStatusCode.Conflict, "PACK_BUILD_IN_PROGRESS", "A build is already in progress")
        }

        val newVersionId = versionId ?: incrementVersion(existing.versionId)
        val taskId = taskStore.create(instanceId, "pack_build", "Building pack...")

        packStore.update(instanceId) {
            it.copy(buildState = "building", buildProgress = 0.0, buildMessage = "Starting build...")
        }

        scope.launch {
            try {
                taskStore.updateProgress(taskId, 0.3, "Generating modrinth.index.json...")
                packStore.update(instanceId) {
                    it.copy(buildProgress = 0.3, buildMessage = "Generating modrinth.index.json...")
                }

                val mods = modStore.list(instanceId).filter { it.enabled }
                val instance = instanceStore.get(instanceId)
                val mrpackBytes = generateMrpack(instanceId, instance, mods, newVersionId, summary)

                taskStore.updateProgress(taskId, 0.8, "Writing pack file...")
                val packDir = dataDirectory.packDir(instanceId)
                packDir.createDirectories()
                val mrpackPath = dataDirectory.packMrpackPath(instanceId)
                mrpackPath.writeBytes(mrpackBytes)

                val sha256 = MessageDigest.getInstance("SHA-256").digest(mrpackBytes)
                    .joinToString("") { "%02x".format(it) }
                val now = java.time.Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)

                packStore.update(instanceId) {
                    it.copy(
                        versionId = newVersionId,
                        lastBuiltAt = now,
                        dirty = false,
                        fileSize = mrpackBytes.size.toLong(),
                        sha256 = sha256,
                        buildState = "done",
                        buildProgress = 1.0,
                        buildMessage = "Build complete",
                    )
                }
                taskStore.complete(taskId, success = true, "Pack built successfully")
            } catch (e: Exception) {
                packStore.update(instanceId) {
                    it.copy(buildState = "error", buildMessage = "Build failed: ${e.message}")
                }
                taskStore.complete(taskId, success = false, "Build failed: ${e.message}")
            }
        }

        return taskId
    }

    fun getBuildStatus(instanceId: String): BuildStatusResponse {
        val pack = packStore.get(instanceId)
        return BuildStatusResponse(
            state = pack?.buildState ?: "idle",
            progress = pack?.buildProgress ?: 0.0,
            message = pack?.buildMessage ?: "",
        )
    }

    private fun generateMrpack(
        instanceId: String,
        instance: InstanceSummary,
        mods: List<InstalledMod>,
        versionId: String,
        summary: String?,
    ): ByteArray {
        val indexJson = buildModrinthIndex(instanceId, instance, mods, versionId, summary)
        val buffer = java.io.ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            zip.putNextEntry(ZipEntry("modrinth.index.json"))
            zip.write(indexJson.toByteArray())
            zip.closeEntry()
        }
        return buffer.toByteArray()
    }

    private fun buildModrinthIndex(
        instanceId: String,
        instance: InstanceSummary,
        mods: List<InstalledMod>,
        versionId: String,
        summary: String?,
    ): String {
        val files = mods.map { mod ->
            val downloadUrl = mod.downloadUrl ?: "/public/$instanceId/mods/${mod.fileName}"
            MrpackFile(
                path = "mods/${mod.fileName}",
                hashes = MrpackHashes(sha1 = mod.hashes.sha1 ?: "", sha512 = mod.hashes.sha512 ?: ""),
                downloads = listOf(downloadUrl),
                fileSize = mod.fileSize,
            )
        }
        val deps = mutableMapOf<String, String>()
        deps["minecraft"] = instance.mcVersion
        instance.loader?.let { loader ->
            deps[loader.type.name.lowercase()] = loader.version
        }
        val index = MrpackIndex(
            formatVersion = 1,
            game = "minecraft",
            versionId = versionId,
            name = instance.name,
            summary = summary,
            files = files,
            dependencies = deps,
        )
        return json.encodeToString(index)
    }

    private fun incrementVersion(current: String): String {
        val parts = current.split(".")
        if (parts.size == 3) {
            val patch = parts[2].toIntOrNull()
            if (patch != null) return "${parts[0]}.${parts[1]}.${patch + 1}"
        }
        return "$current.1"
    }
}

@Serializable
private data class MrpackIndex(
    val formatVersion: Int,
    val game: String,
    val versionId: String,
    val name: String,
    val summary: String? = null,
    val files: List<MrpackFile>,
    val dependencies: Map<String, String>,
)

@Serializable
private data class MrpackFile(
    val path: String,
    val hashes: MrpackHashes,
    val downloads: List<String>,
    val fileSize: Long,
)

@Serializable
private data class MrpackHashes(
    val sha1: String,
    val sha512: String,
)
