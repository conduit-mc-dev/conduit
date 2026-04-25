package dev.conduit.daemon.service

import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class ServerPropertiesService(private val dataDirectory: DataDirectory) {

    fun read(instanceId: String): Map<String, String> {
        val path = propertiesPath(instanceId)
        if (!path.exists()) return emptyMap()
        val props = Properties()
        path.inputStream().use { props.load(it) }
        return props.entries.associate { (k, v) -> k.toString() to v.toString() }
    }

    fun update(instanceId: String, changes: Map<String, String>): List<String> {
        val path = propertiesPath(instanceId)
        val props = Properties()
        if (path.exists()) {
            path.inputStream().use { props.load(it) }
        }
        val updated = mutableListOf<String>()
        for ((key, value) in changes) {
            props.setProperty(key, value)
            updated.add(key)
        }
        path.outputStream().use { props.store(it, null) }
        return updated
    }

    private fun propertiesPath(instanceId: String): Path =
        dataDirectory.instanceDir(instanceId).resolve("server.properties")
}
