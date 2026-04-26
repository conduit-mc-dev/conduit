package dev.conduit.daemon

import dev.conduit.core.model.CreateInstanceRequest
import dev.conduit.core.model.InstanceState
import dev.conduit.core.model.UpdateInstanceRequest
import dev.conduit.daemon.service.DataDirectory
import dev.conduit.daemon.store.InstanceStore
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class InstanceStoreTest {

    private fun createStore(dir: Path): InstanceStore {
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()
        return InstanceStore(dataDir)
    }

    private fun createInstance(store: InstanceStore, name: String = "Test Server"): String {
        val summary = store.create(CreateInstanceRequest(name = name, mcVersion = "1.20.4"))
        store.markInitialized(summary.id)
        return summary.id
    }

    @Test
    fun `persist and reload`() = withTempDir { dir ->
        val id = createInstance(createStore(dir))

        val reloaded = createStore(dir)
        val instance = reloaded.get(id)
        assertEquals("Test Server", instance.name)
        assertEquals("1.20.4", instance.mcVersion)
        assertEquals(InstanceState.STOPPED, instance.state)
    }

    @Test
    fun `state recovery - RUNNING becomes STOPPED`() = withTempDir { dir ->
        val store = createStore(dir)
        val id = createInstance(store)
        store.transitionState(id, InstanceState.STOPPED, InstanceState.STARTING)
        store.transitionState(id, InstanceState.STARTING, InstanceState.RUNNING)

        val reloaded = createStore(dir)
        val instance = reloaded.get(id)
        assertEquals(InstanceState.STOPPED, instance.state)
        assertTrue(instance.statusMessage?.contains("Recovered") == true)
    }

    @Test
    fun `state recovery - INITIALIZING becomes STOPPED`() = withTempDir { dir ->
        val store = createStore(dir)
        val summary = store.create(CreateInstanceRequest(name = "Init Test", mcVersion = "1.20.4"))

        val reloaded = createStore(dir)
        val instance = reloaded.get(summary.id)
        assertEquals(InstanceState.STOPPED, instance.state)
        assertTrue(instance.statusMessage?.contains("interrupted") == true)
    }

    @Test
    fun `state recovery - STOPPED stays STOPPED`() = withTempDir { dir ->
        val id = createInstance(createStore(dir))

        val reloaded = createStore(dir)
        val instance = reloaded.get(id)
        assertEquals(InstanceState.STOPPED, instance.state)
        assertNull(instance.statusMessage)
    }

    @Test
    fun `corrupt JSON is skipped`() = withTempDir { dir ->
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()
        val instanceDir = dataDir.instanceDir("corrupt1")
        instanceDir.createDirectories()
        instanceDir.resolve("instance.json").writeText("not valid json {{{")

        val store = InstanceStore(dataDir)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `directory without instance json is skipped`() = withTempDir { dir ->
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()
        val instanceDir = dataDir.instanceDir("nofile1")
        instanceDir.createDirectories()
        instanceDir.resolve("server.jar").writeText("fake jar")

        val store = InstanceStore(dataDir)
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `delete removes instance json`() = withTempDir { dir ->
        val dataDir = DataDirectory(dir)
        dataDir.ensureDirectories()
        val store = InstanceStore(dataDir)
        val id = createInstance(store)

        assertTrue(dataDir.instanceMetadataPath(id).exists())
        store.delete(id)
        assertFalse(dataDir.instanceMetadataPath(id).exists())
    }

    @Test
    fun `update persists changes`() = withTempDir { dir ->
        val store = createStore(dir)
        val id = createInstance(store)
        store.update(id, UpdateInstanceRequest(name = "Renamed Server"))

        val reloaded = createStore(dir)
        assertEquals("Renamed Server", reloaded.get(id).name)
    }

    @Test
    fun `no dataDirectory - pure in-memory`() {
        val store = InstanceStore()
        val summary = store.create(CreateInstanceRequest(name = "Memory Test", mcVersion = "1.20.4"))
        store.markInitialized(summary.id)
        assertEquals("Memory Test", store.get(summary.id).name)
    }

    @Test
    fun `multiple instances load correctly`() = withTempDir { dir ->
        val store = createStore(dir)
        val id1 = createInstance(store, "Server A")
        val id2 = createInstance(store, "Server B")
        val id3 = createInstance(store, "Server C")

        val reloaded = createStore(dir)
        assertEquals(3, reloaded.list().size)
        assertEquals("Server A", reloaded.get(id1).name)
        assertEquals("Server B", reloaded.get(id2).name)
        assertEquals("Server C", reloaded.get(id3).name)
    }
}
