package dev.conduit.daemon

/**
 * Mock MC server that prints initialization lines but NEVER prints "Done".
 * Used to test startup timeout — the real server would hang similarly on
 * broken world gen, mod conflicts, etc.
 */
object StuckMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Preparing level \"world\"")
        System.out.flush()

        // Block forever — simulates a stuck init. stdin read blocks the main
        // thread, so stdin closure (process destroy) cleanly terminates us.
        val reader = System.`in`.bufferedReader()
        while (reader.readLine() != null) {
            // swallow any input, never print Done
        }
    }
}
