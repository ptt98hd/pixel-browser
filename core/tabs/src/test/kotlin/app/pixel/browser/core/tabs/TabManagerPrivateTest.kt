package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.FakeEngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 016 AC4, AC8–AC11 – tab ẩn danh trong [TabManager] (JVM thuần với FakeEngineSession). */
class TabManagerPrivateTest {
    private val factory = RecordingSessionFactory()
    private val store = InMemoryTabSnapshotStore()

    private val a = "https://a.example/"
    private val b = "https://b.example/"
    private val c = "https://c.example/"

    /** Số lần `onPrivateTabsEmpty` được gọi, và trạng thái `isClosed` của các session ẩn danh tại mỗi lần gọi. */
    private var emptyCalls = 0
    private val closedAtEmptyCall = mutableListOf<Boolean>()
    private val privateSessions = mutableListOf<FakeEngineSession>()

    private fun manager(
        maxLive: Int = TabManager.DEFAULT_MAX_LIVE_SESSIONS,
        s: TabSnapshotStore = store,
        f: EngineSessionFactory = factory,
    ) = TabManager(
        f,
        s,
        maxLive,
        onPrivateTabsEmpty = {
            emptyCalls++
            closedAtEmptyCall += privateSessions.all { it.isClosed }
        },
    )

    private fun TabManager.ids() = tabs.value.map { it.id }

    private fun TabManager.fake(id: String) = session(id) as FakeEngineSession

    private fun TabManager.openPrivate(url: String = a): Tab {
        val t = openTab(url, isPrivate = true)
        privateSessions += fake(t.id)
        return t
    }

    // --- AC4: factory & TabManager ---

    @Test
    fun ac4_openPrivateTab_asksFactoryForPrivateSession() {
        val m = manager()
        val t = m.openTab(a, isPrivate = true)
        assertEquals(listOf(false, true), factory.requestedPrivate)
        assertTrue(t.isPrivate)
        assertTrue(m.fake(t.id).isPrivate)
    }

    @Test
    fun ac4_openNormalTab_asksFactoryForNormalSession() {
        val m = manager()
        val t = m.openTab(a)
        assertEquals(listOf(false, false), factory.requestedPrivate)
        assertFalse(m.fake(t.id).isPrivate)
    }

    @Test
    fun ac4_popupOfPrivateTab_isPrivate() {
        val m = manager()
        val opener = m.openTab(a, isPrivate = true)
        val popup = m.openTabForWindow(opener.id)!!
        assertTrue(popup.isPrivate)
        assertTrue(m.fake(popup.id).isPrivate)
    }

    @Test
    fun ac4_popupOfNormalTab_isNormal() {
        val m = manager()
        val opener = m.openTab(a)
        val popup = m.openTabForWindow(opener.id)!!
        assertFalse(popup.isPrivate)
        assertFalse(m.fake(popup.id).isPrivate)
    }

    @Test
    fun ac4_discardedPrivateTab_revivesWithPrivateSession() {
        val m = manager(maxLive = 2)
        val p = m.openTab(a, isPrivate = true)
        val first = m.fake(p.id)
        m.openTab(b)
        m.openTab(c)
        assertTrue("private tab must be discarded first", first.isClosed)
        assertNull(m.session(p.id))

        m.selectTab(p.id)

        val revived = m.fake(p.id)
        assertTrue(revived.isPrivate)
        assertTrue(
            m.tabs.value
                .first { it.id == p.id }
                .isPrivate,
        )
        assertEquals(true, factory.requestedPrivate.last())
    }

    @Test
    fun ac4_discardedPrivateTab_stillCountsAsPrivateTab() {
        val m = manager(maxLive = 2)
        m.openTab(a, isPrivate = true)
        m.openTab(b)
        m.openTab(c)
        assertEquals(1, m.privateTabCount.value)
        assertEquals(0, emptyCalls)
    }

    @Test
    fun ac4_restoreFromStore_hasNoPrivateTabs() {
        store.stored = TabsSnapshot(listOf(TabSnapshot("t1", a), TabSnapshot("t2", b)), "t1")
        val m = manager()
        assertTrue(m.tabs.value.none { it.isPrivate })
        assertFalse(m.fake(m.selectedTabId.value!!).isPrivate)
        assertEquals(0, m.privateTabCount.value)
    }

    // --- AC8: hết tab ẩn danh ---

    @Test
    fun ac8_privateTabCount_countsPrivateTabs() {
        val m = manager()
        assertEquals(0, m.privateTabCount.value)
        val p1 = m.openPrivate()
        m.openPrivate(b)
        m.openTab(c)
        assertEquals(2, m.privateTabCount.value)
        m.closeTab(p1.id)
        assertEquals(1, m.privateTabCount.value)
    }

    @Test
    fun ac8_closingNonLastPrivateTab_doesNotNotify() {
        val m = manager()
        val p1 = m.openPrivate()
        m.openPrivate(b)
        m.closeTab(p1.id)
        assertEquals(0, emptyCalls)
    }

    @Test
    fun ac8_closingLastPrivateTab_notifiesExactlyOnce_afterSessionClosed() {
        val m = manager()
        val p1 = m.openPrivate()
        val p2 = m.openPrivate(b)
        m.closeTab(p1.id)
        m.closeTab(p2.id)
        assertEquals(1, emptyCalls)
        assertEquals(listOf(true), closedAtEmptyCall)
        assertEquals(0, m.privateTabCount.value)
    }

    @Test
    fun ac8_closeAllTabs_notifiesOnce() {
        val m = manager()
        m.openPrivate()
        m.openPrivate(b)
        m.closeAllTabs()
        assertEquals(1, emptyCalls)
        assertEquals(listOf(true), closedAtEmptyCall)
        assertEquals(0, m.privateTabCount.value)
    }

    @Test
    fun ac8_closePrivateTabs_notifiesOnce() {
        val m = manager()
        m.openPrivate()
        m.openPrivate(b)
        m.closePrivateTabs()
        assertEquals(1, emptyCalls)
        assertEquals(listOf(true), closedAtEmptyCall)
    }

    @Test
    fun ac8_close_notifiesOnce() {
        val m = manager()
        m.openPrivate()
        m.openPrivate(b)
        m.close()
        assertEquals(1, emptyCalls)
        assertEquals(listOf(true), closedAtEmptyCall)
    }

    @Test
    fun ac8_closingNormalTabOrInitialization_doesNotNotify() {
        val m = manager()
        val t = m.openTab(a)
        m.closeTab(t.id)
        m.closeAllTabs()
        assertEquals(0, emptyCalls)
    }

    @Test
    fun ac8_close_withoutPrivateTabs_doesNotNotify() {
        val m = manager()
        m.openTab(a)
        m.close()
        assertEquals(0, emptyCalls)
    }

    @Test
    fun ac8_reopenPrivateTabAndCloseAgain_notifiesAgain() {
        val m = manager()
        val p1 = m.openPrivate()
        m.closeTab(p1.id)
        assertEquals(1, emptyCalls)
        val p2 = m.openPrivate(b)
        m.closeTab(p2.id)
        assertEquals(2, emptyCalls)
    }

    @Test
    fun ac8_discardedPrivateTab_closedLast_notifies() {
        val m = manager(maxLive = 2)
        val p = m.openTab(a, isPrivate = true)
        m.openTab(b)
        m.openTab(c)
        assertNull(m.session(p.id))
        m.closeTab(p.id)
        assertEquals(1, emptyCalls)
    }

    // --- AC9: hoàn tác ---

    @Test
    fun ac9_closeNonLastPrivateTab_isUndoable_andRestoresPositionAndPrivacy() {
        val m = manager()
        val home = m.ids().single()
        val p1 = m.openPrivate()
        val p2 = m.openPrivate(b)
        val n1 = m.openTab(c)
        val before = listOf(home, p1.id, p2.id, n1.id)
        assertEquals(before, m.ids())

        assertTrue(m.closeTab(p1.id))

        val undone = m.undoClose()
        assertNotNull(undone)
        assertEquals(p1.id, undone!!.id)
        assertTrue(undone.isPrivate)
        assertEquals(before, m.ids())
        assertTrue(
            m.tabs.value
                .first { it.id == p1.id }
                .isPrivate,
        )
    }

    @Test
    fun ac9_closeLastPrivateTab_isNotUndoable() {
        val m = manager()
        val p = m.openPrivate()
        assertFalse(m.closeTab(p.id))
        assertNull(m.undoClose())
    }

    @Test
    fun ac9_closingLastPrivateTab_scrubsEarlierClosedPrivateTabsFromUndoStack() {
        val m = manager()
        val p1 = m.openPrivate()
        val p2 = m.openPrivate(b)
        val n1 = m.openTab(c)
        assertTrue(m.closeTab(n1.id))
        assertTrue(m.closeTab(p1.id))
        assertFalse(m.closeTab(p2.id))

        val undone = generateSequence { m.undoClose() }.toList()

        assertEquals(listOf(n1.id), undone.map { it.id })
        assertTrue(m.tabs.value.none { it.isPrivate })
    }

    @Test
    fun ac9_undoNeverReturnsPrivateTab_onceSpaceIsEmpty() {
        val m = manager()
        val p1 = m.openPrivate()
        val p2 = m.openPrivate(b)
        m.closeTab(p1.id)
        m.closeTab(p2.id)
        assertNull(m.undoClose())
        assertEquals(0, m.privateTabCount.value)
    }

    @Test
    fun ac9_closeAllTabs_normalUndoable_privateNot() {
        val m = manager()
        val home = m.ids().single()
        val n1 = m.openTab(a)
        val p = m.openPrivate(b)

        m.closeAllTabs()
        val undone = generateSequence { m.undoClose() }.toList()

        assertEquals(setOf(home, n1.id), undone.map { it.id }.toSet())
        assertTrue(undone.none { it.id == p.id || it.isPrivate })
        assertTrue(m.tabs.value.none { it.isPrivate })
    }

    @Test
    fun ac9_closeNormalTab_isUndoable() {
        val m = manager()
        val n = m.openTab(a)
        assertTrue(m.closeTab(n.id))
        assertEquals(n.id, m.undoClose()?.id)
    }

    @Test
    fun ac9_closeNormalTab_whilePrivateTabsExist_isUndoable() {
        val m = manager()
        m.openPrivate()
        val n = m.openTab(a)
        assertTrue(m.closeTab(n.id))
    }

    // --- AC10: closePrivateTabs ---

    @Test
    fun ac10_closePrivateTabs_closesAllPrivate_selectsMostRecentlyUsedNormal_keepsOrder() {
        val m = manager()
        val n1 = m.ids().single()
        val p1 = m.openPrivate()
        val n2 = m.openTab(c)
        m.selectTab(n1) // n1 là tab thường dùng gần nhất (không phải tab kề vị trí)
        val p2 = m.openPrivate(b) // đang chọn p2
        assertEquals(p2.id, m.selectedTabId.value)

        val closed = m.closePrivateTabs()

        assertEquals(2, closed)
        assertEquals(listOf(n1, n2.id), m.ids())
        assertEquals(n1, m.selectedTabId.value)
        assertTrue(privateSessions.all { it.isClosed })
        assertTrue(m.tabs.value.none { it.id == p1.id || it.id == p2.id })
    }

    @Test
    fun ac10_closePrivateTabs_onlyPrivateTabs_createsSelectedNormalHomeTab() {
        val m = manager()
        val p = m.openPrivate()
        m.closeTab(m.ids().first { it != p.id })
        assertEquals(listOf(p.id), m.ids())

        assertEquals(1, m.closePrivateTabs())

        val only = m.tabs.value.single()
        assertTrue(only.isHome)
        assertFalse(only.isPrivate)
        assertEquals(only.id, m.selectedTabId.value)
        assertNotEquals(p.id, only.id)
    }

    @Test
    fun ac10_closePrivateTabs_withoutPrivateTabs_returnsZero_andChangesNothing() {
        val m = manager()
        m.openTab(a)
        val before = m.ids()
        val selected = m.selectedTabId.value

        assertEquals(0, m.closePrivateTabs())

        assertEquals(before, m.ids())
        assertEquals(selected, m.selectedTabId.value)
        assertEquals(0, emptyCalls)
    }

    @Test
    fun ac10_closePrivateTabs_isNotUndoable() {
        val m = manager()
        m.openPrivate()
        m.openPrivate(b)
        m.closePrivateTabs()
        assertNull(m.undoClose())
    }

    // --- AC11: không persist ---

    @Test
    fun ac11_snapshot_excludesPrivateTabs_andSelectionIsNeverPrivate() {
        val m = manager()
        val home = m.ids().single()
        val p = m.openPrivate("https://secret-private.example/")
        assertEquals(p.id, m.selectedTabId.value)

        val snap = m.snapshot()

        assertEquals(listOf(home), snap.tabs.map { it.id })
        assertEquals(home, snap.selectedTabId)
        assertTrue(snap.tabs.none { it.url.contains("secret-private") })
    }

    @Test
    fun ac11_close_removesPrivateTabsEntirely_andNotifies() {
        val m = manager()
        val home = m.ids().single()
        val p = m.openPrivate()
        val ps = m.fake(p.id)

        m.close()

        assertTrue(ps.isClosed)
        assertEquals(listOf(home), m.ids())
        assertEquals(0, m.privateTabCount.value)
        assertEquals(1, emptyCalls)
        assertNull("private tab must not be revivable", m.undoClose())
    }

    @Test
    fun ac11_close_thenSelectNormalTab_neverRecreatesPrivateSession() {
        val m = manager()
        val home = m.ids().single()
        m.openPrivate()
        m.close()
        m.selectTab(home)
        assertTrue(factory.created.drop(1).none { it.isPrivate && !it.isClosed })
        assertTrue(m.tabs.value.none { it.isPrivate })
    }
}
