package dev.conduit.daemon.routes

import dev.conduit.core.model.*
import dev.conduit.daemon.store.DaemonConfigStore
import dev.conduit.daemon.store.InstanceStore
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

fun Route.configRoutes(
    daemonConfigStore: DaemonConfigStore,
    instanceStore: InstanceStore,
) {
    route("/api/v1/config") {
        get("/daemon") {
            call.respond(daemonConfigStore.get())
        }

        put("/daemon") {
            val request = call.receive<UpdateDaemonConfigRequest>()
            call.respond(daemonConfigStore.update(request))
        }
    }

    route("/api/v1/instances/{id}") {
        get("/config/jvm") {
            val id = call.requireInstanceId()
            call.respond(buildJvmConfig(id, instanceStore, daemonConfigStore))
        }

        put("/config/jvm") {
            val id = call.requireInstanceId()
            val body = call.receive<JsonObject>()
            val hasJvmArgs = "jvmArgs" in body
            val hasJavaPath = "javaPath" in body

            val jvmArgs = if (hasJvmArgs) {
                body["jvmArgs"]?.let { element ->
                    if (element is JsonNull) null
                    else element.jsonArray.map { it.jsonPrimitive.content }
                }
            } else null

            val javaPath = if (hasJavaPath) {
                body["javaPath"]?.let { element ->
                    if (element is JsonNull) null
                    else element.jsonPrimitive.content
                }
            } else null

            instanceStore.updateJvmConfig(id, hasJvmArgs, jvmArgs, hasJavaPath, javaPath)
            call.respond(buildJvmConfig(id, instanceStore, daemonConfigStore))
        }

        get("/invite") {
            val id = call.requireInstanceId()
            val enabled = instanceStore.isPublicEndpointEnabled(id)
            val url = buildInviteUrl(daemonConfigStore.get().port, id)
            call.respond(InviteInfo(url = url, publicEndpointEnabled = enabled))
        }

        put("/invite") {
            val id = call.requireInstanceId()
            val request = call.receive<UpdateInviteRequest>()
            instanceStore.setPublicEndpointEnabled(id, request.publicEndpointEnabled)
            val url = buildInviteUrl(daemonConfigStore.get().port, id)
            call.respond(InviteInfo(url = url, publicEndpointEnabled = request.publicEndpointEnabled))
        }
    }
}

private fun buildJvmConfig(id: String, instanceStore: InstanceStore, daemonConfigStore: DaemonConfigStore): JvmConfig {
    val data = instanceStore.getJvmConfigData(id)
    return JvmConfig(
        jvmArgs = data.jvmArgs,
        javaPath = data.javaPath,
        effectiveJavaPath = data.javaPath ?: daemonConfigStore.get().defaultJavaPath ?: "java",
    )
}

private fun buildInviteUrl(port: Int, instanceId: String): String =
    "conduit://localhost:$port/$instanceId"
