package dev.conduit.daemon

object MockMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Preparing level \"world\"")
        println("[Server thread/INFO]: Done (1.0s)! For help, type \"help\"")
        System.out.flush()

        val reader = System.`in`.bufferedReader()
        while (true) {
            val line = reader.readLine() ?: break
            when {
                line == "list" -> println("[Server thread/INFO]: There are 0 of a max of 20 players online:")
                line.startsWith("say ") -> println("[Server thread/INFO]: [Server] ${line.removePrefix("say ")}")
                line == "stop" -> {
                    println("[Server thread/INFO]: Stopping the server")
                    println("[Server thread/INFO]: Saving worlds")
                    System.out.flush()
                    return
                }
                else -> println("[Server thread/INFO]: Unknown or empty command. Type \"help\" for help.")
            }
            System.out.flush()
        }
    }
}
