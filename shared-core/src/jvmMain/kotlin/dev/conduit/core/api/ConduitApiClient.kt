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

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(this@ConduitApiClient.json) }
        expectSuccess = false
    }

    fun setBaseUrl(url: String) {
        this.baseUrl = url
    }

    fun setToken(token: String) {
        this.token = token
    }

    suspend fun health(): Map<String, String> =
        get("/public/health")

    suspend fun initiatePairing(): PairInitiateResponse =
        post("/api/v1/pair/initiate")

    suspend fun confirmPairing(code: String, deviceName: String): PairConfirmResponse =
        post("/api/v1/pair/confirm") {
            contentType(ContentType.Application.Json)
            setBody(PairConfirmRequest(code = code, deviceName = deviceName))
        }

    suspend fun listInstances(): List<InstanceSummary> =
        get("/api/v1/instances")

    suspend fun listMinecraftVersions(): List<MinecraftVersion> =
        get("/api/v1/minecraft/versions")

    private suspend inline fun <reified T> get(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T = request(HttpMethod.Get, path, block)

    private suspend inline fun <reified T> post(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T = request(HttpMethod.Post, path, block)

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
        return response.body()
    }

    override fun close() {
        client.close()
    }
}
