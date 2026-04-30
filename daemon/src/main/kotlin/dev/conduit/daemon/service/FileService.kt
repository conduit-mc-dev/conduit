package dev.conduit.daemon.service

import dev.conduit.daemon.ApiException
import io.ktor.http.*
import java.nio.file.FileSystemException
import java.nio.file.Path
import kotlin.io.path.*

class FileService(private val dataDirectory: DataDirectory) {

    companion object {
        const val MAX_FILE_SIZE = 10L * 1024 * 1024 // 10 MB

        private val PROTECTED_PATHS = listOf(
            "server.jar",
            "instance.json",
        )

        private val PROTECTED_PREFIXES = listOf(
            "mods-disabled/",
            "mods-custom/",
            "pack/",
        )

        private val MIME_MAP = mapOf(
            "json" to "application/json",
            "toml" to "application/toml",
            "yaml" to "application/yaml",
            "yml" to "application/yaml",
            "xml" to "application/xml",
            "properties" to "text/plain",
            "txt" to "text/plain",
            "log" to "text/plain",
            "cfg" to "text/plain",
            "conf" to "text/plain",
            "ini" to "text/plain",
            "csv" to "text/csv",
            "html" to "text/html",
            "htm" to "text/html",
            "css" to "text/css",
            "js" to "application/javascript",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "gif" to "image/gif",
            "jar" to "application/java-archive",
            "zip" to "application/zip",
        )
    }

    fun resolveSafePath(instanceId: String, relativePath: String): Path {
        validatePath(relativePath)
        val instanceDir = dataDirectory.instanceDir(instanceId)
        val resolved = instanceDir.resolve(relativePath).normalize()
        if (!resolved.startsWith(instanceDir)) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Invalid path")
        }

        try {
            val realInstanceDir = instanceDir.toRealPath()
            if (resolved.exists()) {
                if (!resolved.toRealPath().startsWith(realInstanceDir)) {
                    throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Invalid path")
                }
            } else {
                val parent = resolved.parent
                if (parent != null && parent.exists()) {
                    val realParent = parent.toRealPath().resolve(resolved.fileName)
                    if (!realParent.startsWith(realInstanceDir)) {
                        throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Invalid path")
                    }
                }
            }
        } catch (_: FileSystemException) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "VALIDATION_ERROR", "Invalid path")
        }

        return resolved
    }

    fun validateNotProtected(relativePath: String) {
        val normalized = relativePath.replace('\\', '/')
        if (normalized in PROTECTED_PATHS) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "FILE_PROTECTED", "Protected file: $relativePath")
        }
        val withSlash = if (normalized.endsWith("/")) normalized else "$normalized/"
        for (prefix in PROTECTED_PREFIXES) {
            if (normalized.startsWith(prefix) || withSlash == prefix) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, "FILE_PROTECTED", "Protected directory: $relativePath")
            }
        }
        if (normalized.startsWith("mods/") && normalized.endsWith(".jar")) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "FILE_PROTECTED", "Protected file: $relativePath")
        }
    }

    fun inferContentType(path: Path): ContentType {
        val ext = path.extension.lowercase()
        val mime = MIME_MAP[ext] ?: "application/octet-stream"
        return ContentType.parse(mime)
    }

    private fun validatePath(relativePath: String) = PathValidator.validateRelativePath(relativePath)
}
