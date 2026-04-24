package dev.conduit.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LoaderType {
    @SerialName("forge") FORGE,
    @SerialName("neoforge") NEOFORGE,
    @SerialName("fabric") FABRIC,
    @SerialName("quilt") QUILT,
}
