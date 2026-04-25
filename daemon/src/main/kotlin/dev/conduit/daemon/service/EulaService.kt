package dev.conduit.daemon.service

import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class EulaService(private val dataDirectory: DataDirectory) {

    fun isAccepted(instanceId: String): Boolean {
        val eulaFile = dataDirectory.instanceDir(instanceId).resolve("eula.txt")
        if (!eulaFile.exists()) return false
        return eulaFile.readText().lines().any {
            it.trim().equals("eula=true", ignoreCase = true)
        }
    }

    fun accept(instanceId: String) {
        val instanceDir = dataDirectory.instanceDir(instanceId)
        instanceDir.createDirectories()
        val eulaFile = instanceDir.resolve("eula.txt")
        eulaFile.writeText(
            "#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\neula=true\n"
        )
    }
}
