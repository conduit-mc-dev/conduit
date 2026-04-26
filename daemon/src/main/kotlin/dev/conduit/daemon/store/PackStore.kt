package dev.conduit.daemon.store

import java.util.concurrent.ConcurrentHashMap

enum class BuildState(val value: String) {
    IDLE("idle"),
    BUILDING("building"),
    DONE("done"),
    ERROR("error"),
}

data class PackMeta(
    val instanceId: String,
    val versionId: String = "0.0.1",
    val lastBuiltAt: String? = null,
    val dirty: Boolean = true,
    val fileSize: Long = 0,
    val sha256: String? = null,
    val buildState: BuildState = BuildState.IDLE,
    val buildProgress: Double = 0.0,
    val buildMessage: String = "",
)

class PackStore {
    private val packs = ConcurrentHashMap<String, PackMeta>()

    fun get(instanceId: String): PackMeta? = packs[instanceId]

    fun getOrCreate(instanceId: String): PackMeta =
        packs.getOrPut(instanceId) { PackMeta(instanceId = instanceId) }

    fun update(instanceId: String, updater: (PackMeta) -> PackMeta) {
        packs.compute(instanceId) { _, existing ->
            updater(existing ?: PackMeta(instanceId = instanceId))
        }
    }

    fun markDirty(instanceId: String) {
        update(instanceId) { it.copy(dirty = true) }
    }
}
