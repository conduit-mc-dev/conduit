package dev.conduit.daemon.routes

import dev.conduit.core.model.MinecraftVersion
import io.ktor.server.response.*
import io.ktor.server.routing.*

// TODO: 迭代 2 替换为 Mojang manifest 获取
private val STUB_VERSIONS = listOf(
    MinecraftVersion("1.21.5", "release", "2025-03-25T10:00:00+00:00"),
    MinecraftVersion("1.21.4", "release", "2024-12-03T10:00:00+00:00"),
    MinecraftVersion("1.21.3", "release", "2024-10-22T10:00:00+00:00"),
    MinecraftVersion("1.21.2", "release", "2024-10-01T10:00:00+00:00"),
    MinecraftVersion("1.21.1", "release", "2024-08-08T10:00:00+00:00"),
    MinecraftVersion("1.21", "release", "2024-06-13T10:00:00+00:00"),
    MinecraftVersion("1.20.6", "release", "2024-04-29T10:00:00+00:00"),
    MinecraftVersion("1.20.4", "release", "2023-12-07T10:00:00+00:00"),
    MinecraftVersion("1.20.2", "release", "2023-09-21T10:00:00+00:00"),
    MinecraftVersion("1.20.1", "release", "2023-06-12T10:00:00+00:00"),
)

fun Route.minecraftRoutes() {
    route("/api/v1/minecraft") {
        get("/versions") {
            call.respond(STUB_VERSIONS)
        }
    }
}
