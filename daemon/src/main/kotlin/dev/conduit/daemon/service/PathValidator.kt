package dev.conduit.daemon.service

import dev.conduit.daemon.ApiException
import io.ktor.http.*
import java.nio.file.Path

object PathValidator {

    fun validateRelativePath(relativePath: String) {
        if (relativePath.contains("..")) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Path traversal not allowed")
        }
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Absolute paths not allowed")
        }
    }

    fun sanitizeFileName(fileName: String): String {
        val name = Path.of(fileName).fileName?.toString()
            ?: throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Invalid filename")
        if (name.isBlank() || name.contains("..")) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Invalid filename")
        }
        return name
    }
}
