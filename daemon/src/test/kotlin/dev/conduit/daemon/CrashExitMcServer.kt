package dev.conduit.daemon

/** Mock MC server that prints Done then exits(1) after 500ms — simulates crash after reaching RUNNING. */
object CrashExitMcServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[main/INFO]: Starting minecraft server version 1.20.4")
        println("[Server thread/INFO]: Done (1.0s)! For help, type \"help\"")
        System.out.flush()
        Thread.sleep(500)
        kotlin.system.exitProcess(1)
    }
}
