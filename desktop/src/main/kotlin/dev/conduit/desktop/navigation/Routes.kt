package dev.conduit.desktop.navigation

import kotlinx.serialization.Serializable

@Serializable
data object PairRoute

@Serializable
data object InstanceListRoute

@Serializable
data object CreateInstanceRoute

@Serializable
data class InstanceDetailRoute(val instanceId: String)

@Serializable
data class ServerPropertiesRoute(val instanceId: String)

@Serializable
data object LauncherRoute

@Serializable
data object SettingsRoute
