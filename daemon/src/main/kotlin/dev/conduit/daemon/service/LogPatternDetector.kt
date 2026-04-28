package dev.conduit.daemon.service

enum class LogEventType {
    SERVER_DONE,
    OOM,
    PORT_CONFLICT,
    CRASH,
}

data class LogEvent(val type: LogEventType)

class LogPatternDetector {

    private val patterns = listOf(
        Regex("""\[.*]: Done \(""") to LogEventType.SERVER_DONE,
        Regex("""java\.lang\.OutOfMemoryError""") to LogEventType.OOM,
        Regex("""(?i)failed to bind to port|address already in use""") to LogEventType.PORT_CONFLICT,
        Regex("""---- Minecraft Crash Report ----""") to LogEventType.CRASH,
    )

    fun detect(line: String): LogEvent? {
        for ((regex, type) in patterns) {
            if (regex.containsMatchIn(line)) {
                return LogEvent(type)
            }
        }
        return null
    }
}
