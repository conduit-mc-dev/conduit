package dev.conduit.desktop.ui.components

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToastManagerTest {

    @Test
    fun `show adds toast to list`() = runTest {
        val manager = ToastManager()
        manager.show(ToastType.Success, "Done")
        val toasts = manager.toasts.first { it.isNotEmpty() }
        assertEquals(1, toasts.size)
        assertEquals("Done", toasts[0].text)
        assertEquals(ToastType.Success, toasts[0].type)
    }

    @Test
    fun `dismiss removes toast by id`() = runTest {
        val manager = ToastManager()
        val id = manager.show(ToastType.Error, "Error")
        manager.dismiss(id)
        val toasts = manager.toasts.first { it.isEmpty() }
        assertTrue(toasts.isEmpty())
    }

    @Test
    fun `toast auto-dismisses after 3 seconds`() = runTest {
        val manager = ToastManager(autoDismissMs = 50L)
        manager.show(ToastType.Warning, "Warning")
        val toasts = manager.toasts.first { it.isEmpty() }
        assertTrue(toasts.isEmpty())
    }

    @Test
    fun `multiple toasts stack in order`() = runTest {
        val manager = ToastManager()
        manager.show(ToastType.Success, "First")
        manager.show(ToastType.Error, "Second")
        val toasts = manager.toasts.first { it.size == 2 }
        assertEquals("First", toasts[0].text)
        assertEquals("Second", toasts[1].text)
    }

    @Test
    fun `show returns unique ids`() = runTest {
        val manager = ToastManager()
        val id1 = manager.show(ToastType.Success, "A")
        val id2 = manager.show(ToastType.Error, "B")
        assertTrue(id1 != id2)
    }
}
