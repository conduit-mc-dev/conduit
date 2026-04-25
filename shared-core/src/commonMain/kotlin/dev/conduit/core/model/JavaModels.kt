package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class JavaInstallation(
    val path: String,
    val version: String,
    val vendor: String,
    val isDefault: Boolean,
)

@Serializable
data class SetDefaultJavaRequest(
    val path: String,
)
