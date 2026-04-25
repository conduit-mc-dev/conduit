package dev.conduit.daemon.store

import dev.conduit.core.model.InstalledMod
import dev.conduit.daemon.ApiException
import io.ktor.http.*
import java.util.concurrent.ConcurrentHashMap

class ModStore {

    private val mods = ConcurrentHashMap<String, ConcurrentHashMap<String, InstalledMod>>()

    fun list(instanceId: String): List<InstalledMod> =
        instanceMods(instanceId).values.toList()

    fun get(instanceId: String, modId: String): InstalledMod =
        instanceMods(instanceId)[modId]
            ?: throw ApiException(HttpStatusCode.NotFound, "MOD_NOT_FOUND", "Mod not found")

    fun add(instanceId: String, mod: InstalledMod) {
        instanceMods(instanceId)[mod.id] = mod
    }

    fun update(instanceId: String, modId: String, mod: InstalledMod) {
        val map = instanceMods(instanceId)
        if (!map.containsKey(modId)) {
            throw ApiException(HttpStatusCode.NotFound, "MOD_NOT_FOUND", "Mod not found")
        }
        map[modId] = mod
    }

    fun remove(instanceId: String, modId: String): InstalledMod {
        val map = instanceMods(instanceId)
        return map.remove(modId)
            ?: throw ApiException(HttpStatusCode.NotFound, "MOD_NOT_FOUND", "Mod not found")
    }

    fun findByHash(instanceId: String, sha1: String): InstalledMod? =
        instanceMods(instanceId).values.firstOrNull { it.hashes.sha1 == sha1 }

    fun findByModrinthVersionId(instanceId: String, versionId: String): InstalledMod? =
        instanceMods(instanceId).values.firstOrNull { it.modrinthVersionId == versionId }

    private fun instanceMods(instanceId: String): ConcurrentHashMap<String, InstalledMod> =
        mods.getOrPut(instanceId) { ConcurrentHashMap() }
}
