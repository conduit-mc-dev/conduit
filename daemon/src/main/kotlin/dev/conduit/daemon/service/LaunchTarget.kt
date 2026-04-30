package dev.conduit.daemon.service

import dev.conduit.core.model.LoaderInfo
import dev.conduit.core.model.LoaderType

sealed class LaunchTarget {
    data object VanillaJar : LaunchTarget()
    data class ArgFile(val argFilePath: String) : LaunchTarget()
}

internal val IS_WINDOWS: Boolean =
    System.getProperty("os.name", "").lowercase().contains("windows")

fun resolveLaunchTarget(loader: LoaderInfo?, isWindows: Boolean = IS_WINDOWS): LaunchTarget {
    if (loader == null) return LaunchTarget.VanillaJar
    val argFileName = if (isWindows) "win_args.txt" else "unix_args.txt"
    return when (loader.type) {
        LoaderType.FABRIC, LoaderType.QUILT -> LaunchTarget.VanillaJar
        LoaderType.FORGE -> LaunchTarget.ArgFile(
            "libraries/net/minecraftforge/forge/${loader.version}/$argFileName"
        )
        LoaderType.NEOFORGE -> LaunchTarget.ArgFile(
            "libraries/net/neoforged/neoforge/${loader.version}/$argFileName"
        )
    }
}
