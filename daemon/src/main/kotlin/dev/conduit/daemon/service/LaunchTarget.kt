package dev.conduit.daemon.service

import dev.conduit.core.model.LoaderInfo
import dev.conduit.core.model.LoaderType

sealed class LaunchTarget {
    data object VanillaJar : LaunchTarget()
    data class ArgFile(val argFilePath: String) : LaunchTarget()
}

fun resolveLaunchTarget(loader: LoaderInfo?): LaunchTarget {
    if (loader == null) return LaunchTarget.VanillaJar
    return when (loader.type) {
        LoaderType.FABRIC, LoaderType.QUILT -> LaunchTarget.VanillaJar
        LoaderType.FORGE -> LaunchTarget.ArgFile(
            "libraries/net/minecraftforge/forge/${loader.version}/unix_args.txt"
        )
        LoaderType.NEOFORGE -> LaunchTarget.ArgFile(
            "libraries/net/neoforged/neoforge/${loader.version}/unix_args.txt"
        )
    }
}
