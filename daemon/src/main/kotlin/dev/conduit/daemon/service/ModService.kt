package dev.conduit.daemon.service

import dev.conduit.core.download.ModrinthClient
import dev.conduit.core.model.*
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.store.InstanceStore
import dev.conduit.daemon.store.ModStore
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.security.MessageDigest
import kotlin.io.path.*

class ModService(
    private val modStore: ModStore,
    private val modrinthClient: ModrinthClient,
    private val instanceStore: InstanceStore,
    private val dataDirectory: DataDirectory,
    private val broadcaster: WsBroadcaster,
    private val json: Json,
) {
    companion object {
        private const val MAX_UPLOAD_SIZE = 256L * 1024 * 1024
    }

    fun listMods(instanceId: String): List<InstalledMod> =
        modStore.list(instanceId)

    suspend fun installFromModrinth(instanceId: String, versionId: String): InstalledMod {
        if (modStore.findByModrinthVersionId(instanceId, versionId) != null) {
            throw ApiException(HttpStatusCode.Conflict, "MOD_ALREADY_INSTALLED", "This version is already installed")
        }

        val version = modrinthClient.getVersion(versionId)

        val file = version.files.firstOrNull()
            ?: throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Version has no files")
        val downloadUrl = file.url
            ?: throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Version file has no download URL")

        val modsDir = dataDirectory.modsDir(instanceId)
        modsDir.createDirectories()
        val destination = modsDir.resolve(file.fileName)

        try {
            modrinthClient.downloadFile(downloadUrl, destination)
        } catch (e: Exception) {
            destination.deleteIfExists()
            throw e
        }

        val mod = InstalledMod(
            id = IdGenerator.generateInstanceId(),
            source = "modrinth",
            modrinthProjectId = null,
            modrinthVersionId = versionId,
            name = version.name,
            version = version.versionNumber,
            fileName = file.fileName,
            env = ModEnvSupport(),
            hashes = ModHashes(sha1 = file.hashes?.sha1, sha512 = file.hashes?.sha512),
            downloadUrl = downloadUrl,
            fileSize = file.fileSize,
            enabled = true,
        )
        modStore.add(instanceId, mod)
        broadcastPackDirty(instanceId, "mod_added", mod.name)
        return mod
    }

    suspend fun uploadCustomMod(
        instanceId: String,
        fileName: String,
        bytes: ByteArray,
        name: String?,
        version: String?,
        env: ModEnvSupport?,
    ): InstalledMod {
        if (bytes.size > MAX_UPLOAD_SIZE) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, "FILE_TOO_LARGE", "File exceeds 256 MB limit")
        }
        if (!fileName.endsWith(".jar")) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "File must be a .jar")
        }

        val hashes = computeHashes(bytes)
        val existing = modStore.findByHash(instanceId, hashes.sha1!!)
        if (existing != null) {
            throw ApiException(HttpStatusCode.Conflict, "MOD_ALREADY_INSTALLED", "A mod with identical hash is already installed")
        }

        val customDir = dataDirectory.modsCustomDir(instanceId)
        customDir.createDirectories()
        customDir.resolve(fileName).writeBytes(bytes)

        val modsDir = dataDirectory.modsDir(instanceId)
        modsDir.createDirectories()
        modsDir.resolve(fileName).writeBytes(bytes)

        val mod = InstalledMod(
            id = IdGenerator.generateInstanceId(),
            source = "custom",
            name = name ?: fileName.removeSuffix(".jar"),
            version = version ?: "unknown",
            fileName = fileName,
            env = env ?: ModEnvSupport(client = "required", server = "required"),
            hashes = hashes,
            fileSize = bytes.size.toLong(),
            enabled = true,
        )
        modStore.add(instanceId, mod)
        broadcastPackDirty(instanceId, "mod_added", mod.name)
        return mod
    }

    suspend fun removeMod(instanceId: String, modId: String) {
        val mod = modStore.remove(instanceId, modId)
        val modsDir = dataDirectory.modsDir(instanceId)
        modsDir.resolve(mod.fileName).deleteIfExists()
        dataDirectory.modsDisabledDir(instanceId).resolve(mod.fileName).deleteIfExists()
        if (mod.source == "custom") {
            dataDirectory.modsCustomDir(instanceId).resolve(mod.fileName).deleteIfExists()
        }
        broadcastPackDirty(instanceId, "mod_removed", mod.name)
    }

    suspend fun updateMod(instanceId: String, modId: String, newVersionId: String): InstalledMod {
        val mod = modStore.get(instanceId, modId)
        if (mod.source != "modrinth") {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Custom mods cannot be updated via Modrinth")
        }

        val version = modrinthClient.getVersion(newVersionId)

        val file = version.files.firstOrNull()
            ?: throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Version has no files")
        val downloadUrl = file.url
            ?: throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Version file has no download URL")

        val modsDir = dataDirectory.modsDir(instanceId)
        modsDir.resolve(mod.fileName).deleteIfExists()

        val destination = modsDir.resolve(file.fileName)
        modrinthClient.downloadFile(downloadUrl, destination)

        val updated = mod.copy(
            modrinthVersionId = newVersionId,
            name = version.name,
            version = version.versionNumber,
            fileName = file.fileName,
            hashes = ModHashes(sha1 = file.hashes?.sha1, sha512 = file.hashes?.sha512),
            downloadUrl = downloadUrl,
            fileSize = file.fileSize,
        )
        modStore.update(instanceId, modId, updated)
        broadcastPackDirty(instanceId, "mod_updated", updated.name)
        return updated
    }

    suspend fun toggleMod(instanceId: String, modId: String, enabled: Boolean): InstalledMod {
        val mod = modStore.get(instanceId, modId)
        if (mod.enabled == enabled) return mod

        val modsDir = dataDirectory.modsDir(instanceId)
        val disabledDir = dataDirectory.modsDisabledDir(instanceId)
        disabledDir.createDirectories()

        if (enabled) {
            val src = disabledDir.resolve(mod.fileName)
            val dst = modsDir.resolve(mod.fileName)
            if (src.exists()) src.moveTo(dst, overwrite = true)
        } else {
            val src = modsDir.resolve(mod.fileName)
            val dst = disabledDir.resolve(mod.fileName)
            if (src.exists()) src.moveTo(dst, overwrite = true)
        }

        val updated = mod.copy(enabled = enabled)
        modStore.update(instanceId, modId, updated)
        broadcastPackDirty(instanceId, if (enabled) "mod_enabled" else "mod_disabled", updated.name)
        return updated
    }

    suspend fun checkUpdates(instanceId: String): ModUpdatesResponse {
        val mods = modStore.list(instanceId)
        val modrinthMods = mods.filter { it.source == "modrinth" && it.modrinthVersionId != null }
        val updates = mutableListOf<ModUpdateEntry>()

        for (mod in modrinthMods) {
            try {
                val projectId = mod.modrinthProjectId ?: continue
                val versions = modrinthClient.getProjectVersions(projectId)
                val latest = versions.firstOrNull() ?: continue
                if (latest.versionId != mod.modrinthVersionId) {
                    updates.add(
                        ModUpdateEntry(
                            modId = mod.id,
                            name = mod.name,
                            currentVersion = mod.version,
                            latestVersion = latest.versionNumber,
                            latestVersionId = latest.versionId,
                            changelog = latest.changelog,
                        )
                    )
                }
            } catch (_: Exception) {
                // skip mods that fail to check
            }
        }

        return ModUpdatesResponse(updatesAvailable = updates.size, mods = updates)
    }

    private fun computeHashes(bytes: ByteArray): ModHashes {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val sha512 = MessageDigest.getInstance("SHA-512").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return ModHashes(sha1 = sha1, sha512 = sha512)
    }

    private suspend fun broadcastPackDirty(instanceId: String, reason: String, modName: String) {
        val payload = buildJsonObject {
            put("reason", JsonPrimitive(reason))
            put("modName", JsonPrimitive(modName))
        }
        broadcaster.broadcast(instanceId, WsMessage.PACK_DIRTY, payload)
    }
}
