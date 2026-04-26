package dev.conduit.daemon

import dev.conduit.core.download.ModrinthClient
import dev.conduit.core.download.MojangClient
import dev.conduit.core.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
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

inline fun <T> withTempDir(prefix: String = "conduit-test", block: (Path) -> T): T {
    val dir = Files.createTempDirectory(prefix)
    try {
        return block(dir)
    } finally {
        dir.toFile().deleteRecursively()
    }
}

private val mockJarContent = "fake-server-jar-content".toByteArray()
private val mockJarSha1 = "0a08e2d523081e88ffef01e923c6f2de074108e7"

private val mockManifestJson = """
{
  "latest": {"release":"1.20.4","snapshot":"1.20.4"},
  "versions": [{
    "id": "1.20.4",
    "type": "release",
    "url": "https://piston-meta.mojang.com/v1/packages/abc/1.20.4.json",
    "releaseTime": "2023-12-07T00:00:00Z"
  }]
}
""".trimIndent()

private val mockVersionDetailJson = """
{
  "id": "1.20.4",
  "type": "release",
  "downloads": {
    "server": {
      "sha1": "$mockJarSha1",
      "size": ${mockJarContent.size},
      "url": "https://piston-data.mojang.com/v1/objects/abc/server.jar"
    }
  }
}
""".trimIndent()

private val mockModJarBytes = "PK-fake-modrinth-mod".toByteArray()

private val mockVersionV1Json = """
{
  "id": "ver-001",
  "project_id": "proj-abc",
  "version_number": "1.0.0",
  "name": "Sodium 1.0.0",
  "changelog": "Initial release",
  "game_versions": ["1.20.4"],
  "loaders": ["fabric"],
  "date_published": "2024-01-01T00:00:00Z",
  "files": [{
    "filename": "sodium-1.0.0.jar",
    "url": "https://cdn.modrinth.com/data/proj-abc/versions/ver-001/sodium-1.0.0.jar",
    "size": ${mockModJarBytes.size},
    "hashes": {"sha1": "aabbccdd", "sha512": "eeff0011"}
  }],
  "dependencies": []
}
""".trimIndent()

private val mockVersionV2Json = """
{
  "id": "ver-002",
  "project_id": "proj-abc",
  "version_number": "1.1.0",
  "name": "Sodium 1.1.0",
  "changelog": "Performance improvements",
  "game_versions": ["1.20.4"],
  "loaders": ["fabric"],
  "date_published": "2024-02-01T00:00:00Z",
  "files": [{
    "filename": "sodium-1.1.0.jar",
    "url": "https://cdn.modrinth.com/data/proj-abc/versions/ver-002/sodium-1.1.0.jar",
    "size": ${mockModJarBytes.size},
    "hashes": {"sha1": "11223344", "sha512": "55667788"}
  }],
  "dependencies": []
}
""".trimIndent()

private val mockProjectVersionsJson = "[$mockVersionV2Json, $mockVersionV1Json]"

fun createMockModrinthClient(): ModrinthClient {
    val httpClient = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.contains("/version/ver-001") -> respond(mockVersionV1Json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("/version/ver-002") -> respond(mockVersionV2Json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("/project/proj-abc/version") -> respond(mockProjectVersionsJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("sodium-") -> respond(mockModJarBytes, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            else -> respondError(HttpStatusCode.NotFound)
        }
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        expectSuccess = false
    }
    return ModrinthClient(httpClient)
}

fun createMockMojangClient(): MojangClient {
    val httpClient = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.contains("version_manifest") -> respond(mockManifestJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("1.20.4.json") -> respond(mockVersionDetailJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            path.contains("server.jar") -> respond(mockJarContent, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            else -> respondError(HttpStatusCode.NotFound)
        }
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        expectSuccess = true
    }
    return MojangClient(httpClient = httpClient)
}
