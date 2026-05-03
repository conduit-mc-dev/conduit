package dev.conduit.desktop.navigation

import kotlinx.serialization.Serializable

@Serializable object PairRoute
@Serializable object InstanceListRoute
@Serializable object CreateInstanceRoute
@Serializable data class InstanceDetailRoute(val instanceId: String, val daemonId: String)
@Serializable data class DaemonEditRoute(val daemonId: String)
@Serializable object LauncherRoute
@Serializable object SettingsRoute
