package dev.conduit.daemon.service

import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.ModStore
import dev.conduit.daemon.store.BuildState
import dev.conduit.daemon.store.PackStore
import dev.conduit.daemon.store.TaskStore
import dev.conduit.daemon.store.TaskStatus
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
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
    private val buildJobs = ConcurrentHashMap<String, Job>()

    /** Test hook: delay in ms before generating the mrpack. Default 0 (no delay). */
    @Volatile var buildDelayMs: Long = 0

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
        if (existing.buildState == BuildState.BUILDING) {
            throw ApiException(HttpStatusCode.Conflict, "PACK_BUILD_IN_PROGRESS", "A build is already in progress")
        }

        val newVersionId = versionId ?: incrementVersion(existing.versionId)
        val taskId = taskStore.create(instanceId, TaskStore.TYPE_PACK_BUILD, "Building pack...")

        packStore.update(instanceId) {
            it.copy(buildState = BuildState.BUILDING, buildProgress = 0.0, buildMessage = "Starting build...")
        }

        val job = scope.launch {
            try {
                taskStore.updateProgress(taskId, 0.3, "Generating modrinth.index.json...")
                packStore.update(instanceId) {
                    it.copy(buildProgress = 0.3, buildMessage = "Generating modrinth.index.json...")
                }

                if (buildDelayMs > 0) delay(buildDelayMs)

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
                        buildState = BuildState.DONE,
                        buildProgress = 1.0,
                        buildMessage = "Build complete",
                    )
                }
                taskStore.complete(taskId, success = true, "Pack built successfully")
            } catch (e: CancellationException) {
                dataDirectory.packMrpackPath(instanceId).deleteIfExists()
                packStore.update(instanceId) {
                    it.copy(buildState = BuildState.IDLE, buildProgress = 0.0, buildMessage = "")
                }
                taskStore.cancel(taskId, "Build cancelled")
                throw e
            } catch (e: Exception) {
                packStore.update(instanceId) {
                    it.copy(buildState = BuildState.ERROR, buildMessage = "Build failed: ${e.message}")
                }
                taskStore.complete(taskId, success = false, "Build failed: ${e.message}")
            }
        }
        buildJobs[taskId] = job

        return taskId
    }

    fun getBuildStatus(instanceId: String): BuildStatusResponse {
        val pack = packStore.get(instanceId)
        return BuildStatusResponse(
            state = (pack?.buildState ?: BuildState.IDLE).value,
            progress = pack?.buildProgress ?: 0.0,
            message = pack?.buildMessage ?: "",
        )
    }

    suspend fun cancelBuild(taskId: String) {
        val task = taskStore.get(taskId) ?: return
        if (task.status != TaskStatus.RUNNING) return
        buildJobs.remove(taskId)?.cancel()
        // Clean up regardless of whether the coroutine started
        dataDirectory.packMrpackPath(task.instanceId).deleteIfExists()
        packStore.update(task.instanceId) {
            it.copy(buildState = BuildState.IDLE, buildProgress = 0.0, buildMessage = "")
        }
        taskStore.cancel(taskId, "Build cancelled by user")
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
