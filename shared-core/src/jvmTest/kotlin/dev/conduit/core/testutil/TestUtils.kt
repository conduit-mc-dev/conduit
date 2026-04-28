package dev.conduit.core.testutil

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

inline fun <T> withTempDir(prefix: String = "conduit-test", block: (Path) -> T): T {
    val dir = Files.createTempDirectory(prefix)
    try {
        return block(dir)
    } finally {
        dir.toFile().deleteRecursively()
    }
}

fun mockHttpClient(
    expectSuccess: Boolean = true,
    requestedUrls: MutableList<String>? = null,
    handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient {
    return HttpClient(MockEngine { request ->
        requestedUrls?.add(request.url.toString())
        handler(request)
    }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        this.expectSuccess = expectSuccess
    }
}

fun MockRequestHandleScope.jsonResponse(body: String): HttpResponseData =
    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

fun loadFixture(path: String): String =
    object {}.javaClass.getResource("/fixtures/$path")?.readText()
        ?: error("Test fixture not found: /fixtures/$path")
