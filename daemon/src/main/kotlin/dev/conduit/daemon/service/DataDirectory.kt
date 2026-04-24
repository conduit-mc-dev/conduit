package dev.conduit.daemon.service

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

class DataDirectory(
    basePath: Path = System.getenv("CONDUIT_DATA_DIR")?.let { Path(it) } ?: Path("conduit-data"),
) {
    val root: Path = basePath.toAbsolutePath()
    val instancesDir: Path = root.resolve("instances")

    fun instanceDir(instanceId: String): Path =
        instancesDir.resolve(instanceId)

    fun serverJarPath(instanceId: String): Path =
        instanceDir(instanceId).resolve("server.jar")

    fun ensureDirectories() {
        instancesDir.createDirectories()
    }
}
