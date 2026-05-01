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

fun loadFixture(path: String, replacements: Map<String, String> = emptyMap()): String {
    val content = object {}.javaClass.getResource("/fixtures/$path")?.readText()
        ?: error("Test fixture not found: /fixtures/$path")
    val replaced = replacements.entries.fold(content) { acc, (key, value) -> acc.replace("{{$key}}", value) }
    val leftover = Regex("""\{\{[^}]+}}""").find(replaced)
    check(leftover == null) { "Unreplaced placeholder ${leftover?.value} in fixture /fixtures/$path" }
    return replaced
}

private val mockJarContent = "fake-server-jar-content".toByteArray()
private val mockJarSha1 = "0a08e2d523081e88ffef01e923c6f2de074108e7"

private val mockManifestJson = loadFixture("mojang/version_manifest.json")

private val mockVersionDetailJson = loadFixture(
    "mojang/version_detail.json",
    mapOf("SHA1" to mockJarSha1, "SIZE" to mockJarContent.size.toString()),
)

private val mockModJarBytes = "PK-fake-modrinth-mod".toByteArray()

private val mockVersionV1Json = loadFixture(
    "modrinth/version_sodium_1.0.0.json",
    mapOf("SIZE" to mockModJarBytes.size.toString()),
)

private val mockVersionV2Json = loadFixture(
    "modrinth/version_sodium_1.1.0.json",
    mapOf("SIZE" to mockModJarBytes.size.toString()),
)

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

fun createMockLoaderHttpClient(): HttpClient {
    val forgeMetadata = loadFixture("loader/forge-maven-metadata.xml")
    val neoforgeMetadata = loadFixture("loader/neoforge-maven-metadata.xml")
    return HttpClient(MockEngine { request ->
        val url = request.url.toString()
        when {
            url.contains("meta.fabricmc.net") && url.contains("/versions/loader/") ->
                respond(
                    """[{"loader":{"version":"0.16.14"}},{"loader":{"version":"0.16.13"}}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            url.contains("meta.quiltmc.org") && url.contains("/versions/loader/") ->
                respond(
                    """[{"loader":{"version":"0.26.1"}},{"loader":{"version":"0.26.0"}}]""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            url.contains("maven.minecraftforge.net") && url.endsWith("/forge/maven-metadata.xml") ->
                respond(forgeMetadata, headers = headersOf(HttpHeaders.ContentType, "application/xml"))
            url.contains("maven.neoforged.net") && url.endsWith("/neoforge/maven-metadata.xml") ->
                respond(neoforgeMetadata, headers = headersOf(HttpHeaders.ContentType, "application/xml"))
            else -> respondError(HttpStatusCode.NotFound)
        }
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        expectSuccess = false
    }
}

sealed class MockLoaderResponse {
    data class Bytes(val bytes: ByteArray) : MockLoaderResponse()
    data class Status(val status: HttpStatusCode) : MockLoaderResponse()
}

fun createMockLoaderInstallHttpClient(
    installerVersionsJson: String = """[{"version":"1.0.1","stable":true},{"version":"1.0.0","stable":false}]""",
    fabricServerJarResponse: MockLoaderResponse = MockLoaderResponse.Bytes("fake-fabric-jar".toByteArray()),
    quiltInstallerResponse: MockLoaderResponse = MockLoaderResponse.Status(HttpStatusCode.NotFound),
): HttpClient = HttpClient(MockEngine { request ->
    val url = request.url.toString()
    when {
        url.contains("meta.fabricmc.net") && url.contains("/versions/installer") ->
            respond(installerVersionsJson, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        url.contains("meta.fabricmc.net") && url.contains("/server/jar") ->
            respondLoaderMock(fabricServerJarResponse)
        url.contains("maven.quiltmc.org") && url.contains("/quilt-installer/") ->
            respondLoaderMock(quiltInstallerResponse)
        else -> respondError(HttpStatusCode.NotFound)
    }
}) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    expectSuccess = false
}

private fun MockRequestHandleScope.respondLoaderMock(resp: MockLoaderResponse) = when (resp) {
    is MockLoaderResponse.Bytes ->
        respond(resp.bytes, headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"))
    is MockLoaderResponse.Status ->
        respond("", status = resp.status)
}
