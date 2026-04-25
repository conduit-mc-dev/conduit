package dev.conduit.daemon.service

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

class DataDirectory(
    basePath: Path = System.getenv("CONDUIT_DATA_DIR")?.let { Path(it) } ?: Path("conduit-data"),
) {
    val root: Path = basePath.toAbsolutePath()
    val instancesDir: Path = root.resolve("instances")
    val configPath: Path = root.resolve("config.json")

    fun instanceDir(instanceId: String): Path =
        instancesDir.resolve(instanceId)

    fun serverJarPath(instanceId: String): Path =
        instanceDir(instanceId).resolve("server.jar")

    fun modsDir(instanceId: String): Path =
        instanceDir(instanceId).resolve("mods")

    fun modsDisabledDir(instanceId: String): Path =
        instanceDir(instanceId).resolve("mods-disabled")

    fun modsCustomDir(instanceId: String): Path =
        instanceDir(instanceId).resolve("mods-custom")

    fun packDir(instanceId: String): Path =
        instanceDir(instanceId).resolve("pack")

    fun packMrpackPath(instanceId: String): Path =
        packDir(instanceId).resolve("pack.mrpack")

    fun ensureDirectories() {
        instancesDir.createDirectories()
    }
}
