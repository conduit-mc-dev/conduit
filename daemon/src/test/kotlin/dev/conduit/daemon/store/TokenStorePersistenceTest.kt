package dev.conduit.daemon.store

import dev.conduit.core.model.PairedDevice
import kotlin.test.*
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class TokenStorePersistenceTest {

    private fun tokenStoreWithTempFile(): Pair<TokenStore, Path> {
        val dir = createTempDirectory("tokens-test")
        val file = dir.resolve("tokens.json")
        return TokenStore(persistenceFile = file) to file
    }

    @Test
    fun `persists paired devices after confirmPairing`() {
        val (store, _) = tokenStoreWithTempFile()
        store.generatePairCode()
        val response = store.confirmPairing(
            store.generatePairCode().code, "Test Device"
        )

        val devices = store.listDevices()
        assertEquals(1, devices.size)
        assertEquals("Test Device", devices[0].deviceName)
        assertEquals(response.tokenId, devices[0].tokenId)
    }

    @Test
    fun `reloads paired devices from disk`() {
        val (store1, file) = tokenStoreWithTempFile()
        val code = store1.generatePairCode().code
        val response = store1.confirmPairing(code, "Device A")

        // Create a new store pointing to the same file — simulates daemon restart
        val store2 = TokenStore(persistenceFile = file)
        val devices = store2.listDevices()
        assertEquals(1, devices.size)
        assertEquals("Device A", devices[0].deviceName)
        assertEquals(response.tokenId, devices[0].tokenId)
    }

    @Test
    fun `reloaded tokens still validate`() {
        val (store1, file) = tokenStoreWithTempFile()
        val code = store1.generatePairCode().code
        val response = store1.confirmPairing(code, "Device A")

        val store2 = TokenStore(persistenceFile = file)
        val tokenId = store2.validateToken(response.token)
        assertEquals(response.tokenId, tokenId)
    }

    @Test
    fun `multiple devices survive reload`() {
        val (store1, file) = tokenStoreWithTempFile()

        val code1 = store1.generatePairCode().code
        val r1 = store1.confirmPairing(code1, "Device A")

        val code2 = store1.generatePairCode().code
        val r2 = store1.confirmPairing(code2, "Device B")

        val store2 = TokenStore(persistenceFile = file)
        val devices = store2.listDevices()
        assertEquals(2, devices.size)
        assertEquals(setOf("Device A", "Device B"), devices.map { it.deviceName }.toSet())
        assertNotNull(store2.validateToken(r1.token))
        assertNotNull(store2.validateToken(r2.token))
    }

    @Test
    fun `revoked device not present after reload`() {
        val (store1, file) = tokenStoreWithTempFile()
        val code1 = store1.generatePairCode().code
        val r1 = store1.confirmPairing(code1, "Device A")

        val code2 = store1.generatePairCode().code
        store1.confirmPairing(code2, "Device B")

        store1.revokeDevice(r1.tokenId)

        val store2 = TokenStore(persistenceFile = file)
        val devices = store2.listDevices()
        assertEquals(1, devices.size)
        assertEquals("Device B", devices[0].deviceName)
    }

    @Test
    fun `revokeAll clears persisted devices`() {
        val (store1, file) = tokenStoreWithTempFile()
        store1.confirmPairing(store1.generatePairCode().code, "A")
        store1.confirmPairing(store1.generatePairCode().code, "B")
        store1.revokeAll()

        val store2 = TokenStore(persistenceFile = file)
        assertTrue(store2.listDevices().isEmpty())
    }

    @Test
    fun `empty file loads as no devices`() {
        val (store, file) = tokenStoreWithTempFile()
        val tokensFile = file.toFile()
        tokensFile.parentFile.mkdirs()
        tokensFile.writeText("[]")

        val devices = store.listDevices()
        assertTrue(devices.isEmpty())
    }

    @Test
    fun `corrupt file loads as no devices`() {
        val (store, file) = tokenStoreWithTempFile()
        val tokensFile = file.toFile()
        tokensFile.parentFile.mkdirs()
        tokensFile.writeText("{ bad json }")

        val devices = store.listDevices()
        assertTrue(devices.isEmpty())
    }

    @Test
    fun `missing file means no devices`() {
        val (store, _) = tokenStoreWithTempFile()
        assertTrue(store.listDevices().isEmpty())
    }

    @Test
    fun `hasDevices reflects persistence`() {
        val (store1, file) = tokenStoreWithTempFile()
        assertFalse(store1.hasDevices())

        store1.confirmPairing(store1.generatePairCode().code, "A")

        val store2 = TokenStore(persistenceFile = file)
        assertTrue(store2.hasDevices())
    }

    @Test
    fun `daemonId is stable across reloads`() {
        val (store1, file) = tokenStoreWithTempFile()

        val store2 = TokenStore(persistenceFile = file)
        assertEquals(store1.daemonId, store2.daemonId)
    }
}
