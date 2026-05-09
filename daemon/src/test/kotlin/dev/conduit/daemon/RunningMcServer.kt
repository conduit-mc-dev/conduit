package dev.conduit.daemon

/**
 * Mock MC server that prints "Done" (reaches RUNNING) then blocks on stdin.
 * Used to test kill/stop/restart actions on a RUNNING server.
 */
object RunningMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Done (1.0s)! For help, type \"help\"")
        System.out.flush()

        // Read stdin until closed — simulates a running server that stays up.
        val reader = System.`in`.bufferedReader()
        while (reader.readLine() != null) {
            // swallow commands, stay running
        }
    }
}
