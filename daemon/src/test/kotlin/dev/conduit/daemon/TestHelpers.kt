package dev.conduit.daemon

import dev.conduit.core.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
    install(ContentNegotiation) { json(AppJson) }
}

fun ApplicationTestBuilder.wsClient(): HttpClient = createClient {
    install(ContentNegotiation) { json(AppJson) }
    install(WebSockets)
}

suspend fun pairAndGetToken(client: HttpClient): String {
    val code = client.post("/api/v1/pair/initiate").body<PairInitiateResponse>().code
    return client.post("/api/v1/pair/confirm") {
        contentType(ContentType.Application.Json)
        setBody(PairConfirmRequest(code = code, deviceName = "Test Device"))
    }.body<PairConfirmResponse>().token
}

suspend fun createTestInstance(
    client: HttpClient,
    token: String,
    name: String = "Test Server",
    mcVersion: String = "1.20.4",
    tempDir: Path? = null,
): InstanceSummary {
    val instance = client.post("/api/v1/instances") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(CreateInstanceRequest(name = name, mcVersion = mcVersion))
    }.body<InstanceSummary>()
    if (tempDir != null) {
        tempDir.resolve("instances").resolve(instance.id).createDirectories()
    }
    return instance
}
