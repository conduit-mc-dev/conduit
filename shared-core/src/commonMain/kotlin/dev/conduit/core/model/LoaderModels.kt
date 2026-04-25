package dev.conduit.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AvailableLoader(
    val type: LoaderType,
    val versions: List<String>,
)

@Serializable
data class InstallLoaderRequest(
    val type: LoaderType,
    val version: String,
)
