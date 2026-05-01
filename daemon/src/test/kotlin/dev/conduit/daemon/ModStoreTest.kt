package dev.conduit.daemon

import dev.conduit.core.model.InstalledMod
import dev.conduit.core.model.ModEnvSupport
import dev.conduit.core.model.ModHashes
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.ModStore
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModStoreTest {

    private fun createStore(dir: java.nio.file.Path): ModStore {
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()
        return ModStore(dataDir)
    }

    private fun testMod(id: String = "mod1", name: String = "Test Mod", enabled: Boolean = true) = InstalledMod(
        id = id,
        source = "custom",
        name = name,
        version = "1.0.0",
        fileName = "$name.jar",
        env = ModEnvSupport(client = "required", server = "required"),
        hashes = ModHashes(sha1 = "abc123"),
        enabled = enabled,
    )

    @Test
    fun `mods persist and reload across restart`() {
        val dir = Files.createTempDirectory("modstore-test")
        try {
            val store = createStore(dir)
            store.add("inst1", testMod("mod1", "Test Mod"))

            val reloaded = createStore(dir)
            val mods = reloaded.list("inst1")
            assertEquals(1, mods.size)
            assertEquals("Test Mod", mods[0].name)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `toggle disabled state persists`() {
        val dir = Files.createTempDirectory("modstore-test")
        try {
            val store = createStore(dir)
            store.add("inst1", testMod("mod1", "Toggle Mod", enabled = false))

            val reloaded = createStore(dir)
            val mods = reloaded.list("inst1")
            assertEquals(1, mods.size)
            assertFalse(mods[0].enabled, "disabled state should persist across reload")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `orphan files in mods directory get picked up on recovery`() {
        val dir = Files.createTempDirectory("modstore-test")
        try {
            // First, create a store to create the instance directories
            val store = createStore(dir)
            store.add("inst1", testMod("mod1", "Known Mod"))

            // Drop an orphan .jar file into the mods directory
            val modsDir = DataDirectory(dir).modsDir("inst1")
            modsDir.createDirectories()
            val orphanJarBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + "fake-fabric-mod".toByteArray()
            modsDir.resolve("orphan-mod.jar").writeBytes(orphanJarBytes)

            // Reload — orphan file should be picked up
            val reloaded = createStore(dir)
            val mods = reloaded.list("inst1")
            val orphan = mods.find { it.fileName == "orphan-mod.jar" }
            assertNotNull(orphan, "orphan JAR file should be picked up during recovery")
            assertEquals("orphan-mod", orphan.name, "should extract name from filename")
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
