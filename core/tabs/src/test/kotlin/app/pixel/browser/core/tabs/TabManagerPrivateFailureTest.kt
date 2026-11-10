package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.PrivateUnavailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 016 – tạo session ẩn danh thất bại không được để lại profile đã xin (review). */
class TabManagerPrivateFailureTest {
    private var failPrivate = false
    private var emptyCalls = 0

    private val factory =
        EngineSessionFactory { isPrivate ->
            if (isPrivate && failPrivate) throw PrivateUnavailableException("cannot apply profile")
            FakeEngineSession(isPrivate)
        }

    private fun manager() = TabManager(factory, InMemoryTabSnapshotStore(), onPrivateTabsEmpty = { emptyCalls++ })

    @Test
    fun failureWithNoPrivateTab_runsEmptyCleanupOnce_andRethrows() {
        val m = manager()
        failPrivate = true
        assertThrows(PrivateUnavailableException::class.java) { m.openTab("https://a.example/", isPrivate = true) }
        assertEquals(1, emptyCalls)
        assertTrue(m.tabs.value.none { it.isPrivate })
    }

    @Test
    fun failureWhileOtherPrivateTabExists_doesNotRunCleanup() {
        val m = manager()
        m.openTab("https://a.example/", isPrivate = true)
        failPrivate = true
        assertThrows(PrivateUnavailableException::class.java) { m.openTab("https://b.example/", isPrivate = true) }
        assertEquals(0, emptyCalls)
        assertEquals(1, m.privateTabCount.value)
    }
}
