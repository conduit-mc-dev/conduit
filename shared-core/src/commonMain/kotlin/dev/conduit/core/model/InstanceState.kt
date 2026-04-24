package dev.conduit.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InstanceState {
    @SerialName("initializing") INITIALIZING,
    @SerialName("stopped") STOPPED,
    @SerialName("starting") STARTING,
    @SerialName("running") RUNNING,
    @SerialName("stopping") STOPPING,
}
