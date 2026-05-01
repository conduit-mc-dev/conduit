package dev.conduit.daemon

/**
 * Mock MC server that prints "Done" immediately to enter RUNNING state,
 * then produces sustained high-volume console output from a background thread.
 * Simulates mod loading spam / log-heavy scenarios for WebSocket backpressure testing.
 */
object RapidOutputMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Done (1.0s)! For help, type \"help\"")
        System.out.flush()

        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        @Suppress("unused")
        val spammer = Thread {
            var i = 0L
            while (running.get()) {
                println("[Worker-Main-$i/INFO]: [Tick $i] chunk updates, block scans, entity AI, pathfinding, mob spawning, food decay complete — 0.05ms elapsed, 512 chunks processed, TPS 20.0")
                if (++i % 100L == 0L) {
                    System.out.flush()
                }
                try {
                    Thread.sleep(2)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { isDaemon = true }.also { it.start() }

        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line == "stop" -> {
                    running.set(false)
                    println("[Server thread/INFO]: Stopping the server")
                    System.out.flush()
                    return
                }
                line == "list" -> println("[Server thread/INFO]: There are 0 of a max of 20 players online:")
                else -> println("[Server thread/INFO]: Unknown command. Type \"help\" for help.")
            }
            System.out.flush()
        }
    }
}
