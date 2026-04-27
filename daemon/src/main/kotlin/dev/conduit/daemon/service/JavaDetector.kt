package dev.conduit.daemon.service

import dev.conduit.core.model.JavaInstallation
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

class JavaDetector {

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("windows")

    fun detectInstallations(): List<JavaInstallation> {
        val candidates = findCandidates()
        val installations = candidates.mapNotNull { path -> probeJava(path) }
        val currentDefault = installations.firstOrNull()?.path
        return installations.map { it.copy(isDefault = it.path == currentDefault) }
    }

    fun validateJavaPath(path: String): JavaInstallation? {
        return probeJava(path)
    }

    private fun findCandidates(): List<String> {
        val candidates = mutableSetOf<String>()
        val javaBinary = if (isWindows) "java.exe" else "java"

        System.getenv("JAVA_HOME")?.let { javaHome ->
            val bin = Path.of(javaHome, "bin", javaBinary)
            if (bin.exists()) candidates.add(bin.toString())
        }

        findInPath(javaBinary)?.let { candidates.add(it) }

        when {
            osName.contains("mac") -> {
                scanDir("/Library/Java/JavaVirtualMachines", "Contents/Home/bin/java", candidates)
                scanDir(System.getProperty("user.home") + "/Library/Java/JavaVirtualMachines", "Contents/Home/bin/java", candidates)
            }
            osName.contains("linux") -> {
                scanDir("/usr/lib/jvm", "bin/java", candidates)
                scanDir("/usr/java", "bin/java", candidates)
                scanDir("/usr/local/java", "bin/java", candidates)
            }
            isWindows -> {
                scanDir("C:\\Program Files\\Java", "bin\\java.exe", candidates)
                scanDir("C:\\Program Files (x86)\\Java", "bin\\java.exe", candidates)
                scanDir(System.getenv("LOCALAPPDATA") + "\\Programs\\Eclipse Adoptium", "bin\\java.exe", candidates)
            }
        }
        return candidates.toList()
    }

    private fun scanDir(baseDir: String, binarySuffix: String, candidates: MutableSet<String>) {
        val dir = File(baseDir)
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            val javaPath = File(child, binarySuffix)
            if (javaPath.exists() && javaPath.canExecute()) {
                candidates.add(javaPath.absolutePath)
            }
        }
    }

    private fun findInPath(command: String): String? {
        return try {
            val lookupCmd = if (isWindows) "where" else "which"
            val process = ProcessBuilder(lookupCmd, command)
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotBlank()) result.lines().first() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun probeJava(path: String): JavaInstallation? {
        return try {
            val process = ProcessBuilder(path, "-version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() != 0) return null
            parseJavaVersion(path, output)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseJavaVersion(path: String, output: String): JavaInstallation? {
        val firstLine = output.lines().firstOrNull() ?: return null
        val versionRegex = Regex("""version\s+"([^"]+)"""")
        val version = versionRegex.find(firstLine)?.groupValues?.get(1) ?: return null

        val vendor = when {
            output.contains("Eclipse Adoptium", ignoreCase = true) -> "Eclipse Adoptium"
            output.contains("GraalVM", ignoreCase = true) -> "GraalVM"
            output.contains("Azul", ignoreCase = true) -> "Azul Zulu"
            output.contains("Amazon", ignoreCase = true) -> "Amazon Corretto"
            output.contains("Microsoft", ignoreCase = true) -> "Microsoft"
            output.contains("OpenJDK", ignoreCase = true) -> "OpenJDK"
            output.contains("Oracle", ignoreCase = true) -> "Oracle"
            else -> "Unknown"
        }

        return JavaInstallation(
            path = path,
            version = version,
            vendor = vendor,
            isDefault = false,
        )
    }
}
