package dev.conduit.core.api

import dev.conduit.core.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.Closeable

class ConduitApiClient(
    baseUrl: String,
    token: String? = null,
    httpClient: HttpClient? = null,
) : Closeable {

    @Volatile
    var baseUrl: String = baseUrl
        private set

    @Volatile
    var token: String? = token
        private set

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = httpClient ?: HttpClient(CIO) {
        install(ContentNegotiation) { json(this@ConduitApiClient.json) }
        expectSuccess = false
    }

    fun setBaseUrl(url: String) {
        this.baseUrl = url
    }

    fun setToken(token: String) {
        this.token = token
    }

    // --- 公共端点 ---

    suspend fun health(): HealthResponse =
        get("/public/health")

    // --- 配对 ---

    suspend fun initiatePairing(): PairInitiateResponse =
        post("/api/v1/pair/initiate")

    suspend fun confirmPairing(code: String, deviceName: String): PairConfirmResponse =
        post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = deviceName))
        }

    // --- 实例 CRUD ---

    suspend fun listInstances(): List<InstanceSummary> =
        get("/api/v1/instances")

    suspend fun getInstance(id: String): InstanceSummary =
        get("/api/v1/instances/$id")

    suspend fun createInstance(request: CreateInstanceRequest): InstanceSummary =
        post("/api/v1/instances") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun retryDownload(id: String): InstanceSummary =
        post("/api/v1/instances/$id/retry-download")

    suspend fun deleteInstance(id: String): Unit =
        request(HttpMethod.Delete, "/api/v1/instances/$id")

    // --- Minecraft 版本 ---

    suspend fun listMinecraftVersions(): MinecraftVersionsResponse =
        get("/api/v1/minecraft/versions")

    // --- 服务器生命周期 ---

    suspend fun getServerStatus(id: String): ServerStatusResponse =
        get("/api/v1/instances/$id/server/status")

    suspend fun getEula(id: String): EulaResponse =
        get("/api/v1/instances/$id/server/eula")

    suspend fun acceptEula(id: String): EulaResponse =
        put("/api/v1/instances/$id/server/eula") {
            contentType(ContentType.Application.Json)
            setBody(AcceptEulaRequest(accepted = true))
        }

    suspend fun startServer(id: String): ServerStatusResponse =
        post("/api/v1/instances/$id/server/start")

    suspend fun stopServer(id: String): ServerStatusResponse =
        post("/api/v1/instances/$id/server/stop")

    suspend fun killServer(id: String): ServerStatusResponse =
        post("/api/v1/instances/$id/server/kill")

    suspend fun sendCommand(id: String, command: String): CommandAcceptedResponse =
        post("/api/v1/instances/$id/server/command") {
            contentType(ContentType.Application.Json)
            setBody(SendCommandRequest(command = command))
        }

    // --- 配置 ---

    suspend fun getDaemonConfig(): DaemonConfig =
        get("/api/v1/config/daemon")

    suspend fun updateDaemonConfig(request: UpdateDaemonConfigRequest): DaemonConfig =
        put("/api/v1/config/daemon") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun getJvmConfig(instanceId: String): JvmConfig =
        get("/api/v1/instances/$instanceId/config/jvm")

    suspend fun updateJvmConfig(instanceId: String, request: UpdateJvmConfigRequest): JvmConfig =
        put("/api/v1/instances/$instanceId/config/jvm") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    // --- 邀请 ---

    suspend fun getInvite(instanceId: String): InviteInfo =
        get("/api/v1/instances/$instanceId/invite")

    suspend fun updateInvite(instanceId: String, request: UpdateInviteRequest): InviteInfo =
        put("/api/v1/instances/$instanceId/invite") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    // --- Mod 管理 ---

    suspend fun listMods(instanceId: String): List<InstalledMod> =
        get("/api/v1/instances/$instanceId/mods")

    suspend fun installMod(instanceId: String, request: InstallModRequest): InstalledMod =
        post("/api/v1/instances/$instanceId/mods") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun removeMod(instanceId: String, modId: String): Unit =
        request(HttpMethod.Delete, "/api/v1/instances/$instanceId/mods/$modId")

    suspend fun updateMod(instanceId: String, modId: String, request: UpdateModRequest): InstalledMod =
        put("/api/v1/instances/$instanceId/mods/$modId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun toggleMod(instanceId: String, modId: String, request: ToggleModRequest): InstalledMod =
        request(HttpMethod.Patch, "/api/v1/instances/$instanceId/mods/$modId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun checkModUpdates(instanceId: String): ModUpdatesResponse =
        get("/api/v1/instances/$instanceId/mods/updates")

    // --- Modrinth ---

    suspend fun searchModrinth(query: String, mcVersion: String? = null, loader: String? = null): ModrinthSearchResponse {
        val params = buildString {
            append("q=$query")
            mcVersion?.let { append("&mcVersion=$it") }
            loader?.let { append("&loader=$it") }
        }
        return get("/api/v1/modrinth/search?$params")
    }

    suspend fun getModrinthProjectVersions(projectId: String, mcVersion: String? = null): List<ModrinthVersionInfo> {
        val params = mcVersion?.let { "?mcVersion=$it" } ?: ""
        return get("/api/v1/modrinth/project/$projectId/versions$params")
    }

    // --- Java ---

    suspend fun listJavaInstallations(): List<JavaInstallation> =
        get("/api/v1/java/installations")

    suspend fun setDefaultJava(request: SetDefaultJavaRequest): JavaInstallation =
        put("/api/v1/java/default") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    // --- 文件管理 ---

    suspend fun getServerProperties(instanceId: String): Map<String, String> =
        get("/api/v1/instances/$instanceId/config/server-properties")

    suspend fun updateServerProperties(instanceId: String, changes: Map<String, String>): ServerPropertiesUpdateResponse =
        put("/api/v1/instances/$instanceId/config/server-properties") {
            contentType(ContentType.Application.Json)
            setBody(changes)
        }

    suspend fun listFiles(instanceId: String, path: String = ""): DirectoryListing =
        get("/api/v1/instances/$instanceId/files?path=$path")

    suspend fun deleteFile(instanceId: String, path: String): Unit =
        request(HttpMethod.Delete, "/api/v1/instances/$instanceId/files/content?path=$path")

    // --- 内部 HTTP helpers ---

    private suspend inline fun <reified T> get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T = request(HttpMethod.Get, path, block)

    private suspend inline fun <reified T> post(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T = request(HttpMethod.Post, path, block)

    private suspend inline fun <reified T> put(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T = request(HttpMethod.Put, path, block)

    private suspend inline fun <reified T> request(
        method: HttpMethod,
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = client.request("$baseUrl$path") {
            this.method = method
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            block()
        }
        if (!response.status.isSuccess()) {
            val errorBody = try {
                response.body<ErrorResponse>()
            } catch (_: Exception) {
                null
            }
            throw ConduitApiException(response.status.value, errorBody)
        }
        if (response.status == HttpStatusCode.NoContent) {
            @Suppress("UNCHECKED_CAST")
            return Unit as T
        }
        // Use manual JSON deserialization to avoid Ktor MockEngine + ContentNegotiation
        // losing reified type parameters through private inline function chains.
        val text = response.bodyAsText()
        return json.decodeFromString(text)
    }

    override fun close() {
        client.close()
    }
}
