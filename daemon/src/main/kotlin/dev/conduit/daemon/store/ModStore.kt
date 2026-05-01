package dev.conduit.daemon.store

import dev.conduit.core.model.InstalledMod
import dev.conduit.core.model.ModEnvSupport
import dev.conduit.daemon.ApiException
import dev.conduit.daemon.AppJson
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.util.IdGenerator
import io.ktor.http.*
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*

class ModStore(
    private val dataDirectory: DataDirectory? = null,
) {
    private val log = LoggerFactory.getLogger(ModStore::class.java)
    private val mods = ConcurrentHashMap<String, ConcurrentHashMap<String, InstalledMod>>()

    init {
        dataDirectory?.let { loadFromDisk(it) }
    }

    fun list(instanceId: String): List<InstalledMod> =
        instanceMods(instanceId).values.toList()

    fun get(instanceId: String, modId: String): InstalledMod =
        instanceMods(instanceId)[modId]
            ?: throw ApiException(HttpStatusCode.NotFound, "MOD_NOT_FOUND", "Mod not found")

    fun add(instanceId: String, mod: InstalledMod) {
        instanceMods(instanceId)[mod.id] = mod
        persist(instanceId)
    }

    fun update(instanceId: String, modId: String, mod: InstalledMod) {
        val map = instanceMods(instanceId)
        if (!map.containsKey(modId)) {
            throw ApiException(HttpStatusCode.NotFound, "MOD_NOT_FOUND", "Mod not found")
        }
        map[modId] = mod
        persist(instanceId)
    }

    fun remove(instanceId: String, modId: String): InstalledMod {
        val map = instanceMods(instanceId)
        val removed = map.remove(modId)
            ?: throw ApiException(HttpStatusCode.NotFound, "MOD_NOT_FOUND", "Mod not found")
        persist(instanceId)
        return removed
    }

    fun findByHash(instanceId: String, sha1: String): InstalledMod? =
        instanceMods(instanceId).values.firstOrNull { it.hashes.sha1 == sha1 }

    fun findByModrinthVersionId(instanceId: String, versionId: String): InstalledMod? =
        instanceMods(instanceId).values.firstOrNull { it.modrinthVersionId == versionId }

    private fun instanceMods(instanceId: String): ConcurrentHashMap<String, InstalledMod> =
        mods.getOrPut(instanceId) { ConcurrentHashMap() }

    private fun persist(instanceId: String) {
        val dir = dataDirectory ?: return
        try {
            val path = dir.modsMetadataPath(instanceId)
            path.parent.createDirectories()
            path.writeText(AppJson.encodeToString(list(instanceId)))
        } catch (e: Exception) {
            log.warn("Failed to persist mods for {}", instanceId, e)
        }
    }

    private fun loadFromDisk(dir: DataDirectory) {
        if (!dir.instancesDir.exists()) return

        dir.instancesDir.listDirectoryEntries().forEach { instanceDir ->
            if (!instanceDir.isDirectory()) return@forEach
            val instanceId = instanceDir.name
            val metadataPath = dir.modsMetadataPath(instanceId)

            val knownFiles = mutableSetOf<String>()

            if (metadataPath.exists()) {
                try {
                    val loaded = AppJson.decodeFromString<List<InstalledMod>>(metadataPath.readText())
                    loaded.forEach { mod ->
                        instanceMods(instanceId)[mod.id] = mod
                        knownFiles.add(mod.fileName)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load mods.json for {}, scanning directory", instanceId, e)
                }
            }

            // Scan directories for orphan files
            scanOrphanMods(dir, instanceId, "mods", knownFiles)
            scanOrphanMods(dir, instanceId, "mods-disabled", knownFiles, enabled = false)
            scanOrphanMods(dir, instanceId, "mods-custom", knownFiles)
        }
    }

    private fun scanOrphanMods(
        dir: DataDirectory,
        instanceId: String,
        subdir: String,
        knownFiles: MutableSet<String>,
        enabled: Boolean = true,
    ) {
        val scanDir = dir.instanceDir(instanceId).resolve(subdir)
        if (!scanDir.isDirectory()) return

        scanDir.listDirectoryEntries("*.jar").forEach { jarFile ->
            if (jarFile.name in knownFiles) return@forEach
            knownFiles.add(jarFile.name)

            val mod = InstalledMod(
                id = IdGenerator.generateInstanceId(),
                source = if (subdir == "mods-custom") "custom" else "modrinth",
                name = jarFile.name.removeSuffix(".jar"),
                version = "unknown",
                fileName = jarFile.name,
                env = ModEnvSupport(client = "required", server = "required"),
                enabled = enabled,
            )
            instanceMods(instanceId)[mod.id] = mod
            log.info("Recovered orphan mod {} for instance {}", jarFile.name, instanceId)
        }
    }
}
