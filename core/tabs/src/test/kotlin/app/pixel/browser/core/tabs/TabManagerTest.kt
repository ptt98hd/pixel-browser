package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.FakeEngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.function.ThrowingRunnable

/** Spec 002 – JVM thuần với FakeEngineSession. */
class TabManagerTest {
    private val factory = RecordingSessionFactory()
    private val store = InMemoryTabSnapshotStore()

    private val a = "https://a.example/"
    private val b = "https://b.example/"
    private val c = "https://c.example/"

    private fun manager(
        maxLive: Int = TabManager.DEFAULT_MAX_LIVE_SESSIONS,
        s: TabSnapshotStore = store,
        f: EngineSessionFactory = factory,
    ) = TabManager(f, s, maxLive)

    private fun TabManager.ids() = tabs.value.map { it.id }

    private fun TabManager.fake(id: String) = session(id) as FakeEngineSession

    private fun FakeEngineSession.visit(url: String) {
        loadUrl(url)
        simulatePageFinished(url)
    }

    // --- Tab home ban đầu ---

    @Test
    fun emptyStore_startsWithSingleSelectedHomeTab() {
        val m = manager()
        assertEquals(1, m.tabs.value.size)
        val home = m.tabs.value.single()
        assertEquals(HOME_URL, home.url)
        assertFalse(home.isPrivate)
        assertEquals(home.id, m.selectedTabId.value)
    }

    // --- openTab ---

    @Test
    fun openTab_appendsAtEnd_andSelectsByDefault() {
        val m = manager()
        val homeId = m.ids().single()
        val t = m.openTab(a)
        assertEquals(listOf(homeId, t.id), m.ids())
        assertEquals(
            a,
            m.tabs.value
                .last()
                .url,
        )
        assertEquals(t.id, m.selectedTabId.value)
    }

    @Test
    fun openTab_withSelectFalse_appendsButKeepsSelection() {
        val m = manager()
        val homeId = m.ids().single()
        val t = m.openTab(a, select = false)
        assertEquals(listOf(homeId, t.id), m.ids())
        assertEquals(homeId, m.selectedTabId.value)
    }

    @Test
    fun openTab_createsLiveSessionLoadingUrl() {
        val m = manager()
        val t = m.openTab(a, select = false)
        val s = m.session(t.id)
        assertNotNull(s)
        assertEquals(a, s!!.state.value.url)
    }

    @Test
    fun openTab_idsAreUnique() {
        val m = manager()
        repeat(50) { m.openTab("https://site$it.example/", select = it % 2 == 0) }
        assertEquals(m.ids().size, m.ids().toSet().size)
    }

    // --- selectTab ---

    @Test
    fun selectTab_changesSelection() {
        val m = manager()
        val homeId = m.ids().single()
        m.openTab(a)
        m.selectTab(homeId)
        assertEquals(homeId, m.selectedTabId.value)
    }

    @Test
    fun selectTab_unknownId_isNoOp() {
        val m = manager()
        val t = m.openTab(a)
        m.selectTab("does-not-exist")
        assertEquals(t.id, m.selectedTabId.value)
    }

    @Test
    fun switchingTabs_doesNotRecreateOrCloseSessions() {
        val m = manager()
        val homeId = m.ids().single()
        val t1 = m.openTab(a)
        val t2 = m.openTab(b)
        val s1 = m.session(t1.id)
        val s2 = m.session(t2.id)
        val createdBefore = factory.created.size

        m.selectTab(t1.id)
        m.selectTab(homeId)
        m.selectTab(t2.id)
        m.selectTab(t1.id)

        assertEquals(createdBefore, factory.created.size)
        assertSame(s1, m.session(t1.id))
        assertSame(s2, m.session(t2.id))
        assertTrue(factory.created.none { it.isClosed })
    }

    @Test
    fun switchingTabs_keepsSessionHistory() {
        val m = manager()
        val homeId = m.ids().single()
        val t = m.openTab(a)
        m.fake(t.id).simulatePageFinished(a)
        m.fake(t.id).visit(b)
        m.selectTab(homeId)
        m.selectTab(t.id)
        val st = m.session(t.id)!!.state.value
        assertEquals(b, st.url)
        assertTrue(st.canGoBack)
    }

    // --- closeTab ---

    @Test
    fun closeSelectedTab_selectsRightNeighbour() {
        val m = manager()
        val homeId = m.ids().single()
        val t1 = m.openTab(a)
        val t2 = m.openTab(b)
        m.selectTab(t1.id)
        m.closeTab(t1.id)
        assertEquals(listOf(homeId, t2.id), m.ids())
        assertEquals(t2.id, m.selectedTabId.value)
    }

    @Test
    fun closeSelectedLastTab_selectsLeftNeighbour() {
        val m = manager()
        m.openTab(a)
        val t1 = m.ids()[1]
        val t2 = m.openTab(b)
        m.closeTab(t2.id)
        assertEquals(t1, m.selectedTabId.value)
    }

    @Test
    fun closeBackgroundTab_keepsSelection() {
        val m = manager()
        val homeId = m.ids().single()
        val t1 = m.openTab(a)
        m.closeTab(homeId)
        assertEquals(listOf(t1.id), m.ids())
        assertEquals(t1.id, m.selectedTabId.value)
    }

    @Test
    fun closeOnlyTab_createsNewSelectedHomeTab() {
        val m = manager()
        val homeId = m.ids().single()
        m.closeTab(homeId)
        val tab = m.tabs.value.single()
        assertNotEquals(homeId, tab.id)
        assertEquals(HOME_URL, tab.url)
        assertEquals(tab.id, m.selectedTabId.value)
    }

    @Test
    fun closeTab_closesItsEngineSession() {
        val m = manager()
        val t = m.openTab(a)
        val s = m.fake(t.id)
        m.closeTab(t.id)
        assertTrue(s.isClosed)
        assertNull(m.session(t.id))
    }

    @Test
    fun closeTab_unknownId_isNoOp() {
        val m = manager()
        val before = m.ids()
        m.closeTab("does-not-exist")
        assertEquals(before, m.ids())
    }

    // --- undoClose ---

    @Test
    fun undoClose_withNothingClosed_returnsNull() {
        assertNull(manager().undoClose())
    }

    @Test
    fun undoClose_restoresTabWithSameIdAtOriginalPosition() {
        val m = manager()
        val t1 = m.openTab(a)
        m.openTab(b)
        val order = m.ids()
        m.closeTab(t1.id)
        val restored = m.undoClose()
        assertNotNull(restored)
        assertEquals(t1.id, restored!!.id)
        assertEquals(a, restored.url)
        assertEquals(order, m.ids())
    }

    @Test
    fun undoClose_restoresNavigationHistory() {
        val m = manager()
        val t = m.openTab(a)
        m.fake(t.id).simulatePageFinished(a)
        m.fake(t.id).visit(b)
        m.closeTab(t.id)
        m.undoClose()
        m.selectTab(t.id)
        val st = m.session(t.id)!!.state.value
        assertEquals(b, st.url)
        assertTrue(st.canGoBack)
    }

    @Test
    fun undoClose_isLastInFirstOut() {
        val m = manager()
        val t1 = m.openTab(a)
        val t2 = m.openTab(b)
        m.closeTab(t1.id)
        m.closeTab(t2.id)
        assertEquals(t2.id, m.undoClose()!!.id)
        assertEquals(t1.id, m.undoClose()!!.id)
        assertNull(m.undoClose())
    }

    @Test
    fun undoClose_keepsAtMostTenRecentlyClosedTabs() {
        val m = manager()
        val opened = (0 until 11).map { m.openTab("https://site$it.example/") }
        opened.forEach { m.closeTab(it.id) }
        val undone = generateSequence { m.undoClose() }.toList()
        assertEquals(TabManager.MAX_UNDO, undone.size)
        // 10 tab gần nhất (đóng sau cùng) được giữ; tab đóng đầu tiên bị bỏ.
        assertEquals(opened.drop(1).reversed().map { it.id }, undone.map { it.id })
    }

    // --- Discard khi vượt giới hạn session sống ---

    @Test
    fun exceedingMaxLiveSessions_discardsOldestBackgroundTab() {
        val m = manager(maxLive = 2)
        val homeId = m.ids().single()
        val homeSession = m.session(homeId)
        m.openTab(a)
        m.openTab(b)
        assertNull(m.session(homeId))
        assertTrue(homeSession == null || (homeSession as FakeEngineSession).isClosed)
        assertTrue(homeId in m.ids())
        assertEquals(2, factory.created.count { !it.isClosed })
    }

    @Test
    fun discard_choosesLeastRecentlySelectedBackgroundTab() {
        val m = manager(maxLive = 2)
        val homeId = m.ids().single()
        val t1 = m.openTab(a)
        m.selectTab(homeId)
        val t2 = m.openTab(b)
        assertNull(m.session(t1.id))
        assertNotNull(m.session(t2.id))
        assertEquals(t2.id, m.selectedTabId.value)
    }

    @Test
    fun discard_neverDiscardsSelectedTab() {
        val m = manager(maxLive = 1)
        val t1 = m.openTab(a)
        assertNotNull(m.session(t1.id))
        val t2 = m.openTab(b, select = false)
        assertNotNull(m.session(t1.id))
        assertEquals(t1.id, m.selectedTabId.value)
        assertTrue(t2.id in m.ids())
    }

    @Test
    fun defaultMaxLiveSessions_isSix() {
        val m = manager()
        assertEquals(6, m.maxLiveSessions)
        repeat(10) { m.openTab("https://site$it.example/") }
        assertEquals(6, factory.created.count { !it.isClosed })
    }

    @Test
    fun selectingDiscardedTab_recreatesSessionFromSnapshot() {
        val m = manager(maxLive = 2)
        val t1 = m.openTab(a)
        m.fake(t1.id).simulatePageFinished(a)
        m.fake(t1.id).visit(b)
        val old = m.fake(t1.id)
        m.openTab(c)
        m.openTab("https://d.example/")
        assertNull(m.session(t1.id))
        assertTrue(old.isClosed)

        m.selectTab(t1.id)
        val revived = m.session(t1.id)
        assertNotNull(revived)
        assertTrue(revived !== old)
        assertEquals(b, revived!!.state.value.url)
        assertTrue(revived.state.value.canGoBack)
    }

    @Test
    fun discardedTab_staysInSnapshotWithEngineState() {
        val m = manager(maxLive = 2)
        val t1 = m.openTab(a)
        m.fake(t1.id).simulatePageFinished(a)
        m.fake(t1.id).visit(b)
        m.openTab(c)
        m.openTab("https://d.example/")
        val snap = m.snapshot().tabs.single { it.id == t1.id }
        assertEquals(b, snap.url)
        assertNotNull(snap.engineState)
    }

    // --- Snapshot / restore ---

    @Test
    fun snapshot_reflectsCurrentUrlFromEngine() {
        val m = manager()
        val t = m.openTab(a)
        m.fake(t.id).simulatePageFinished(a)
        m.fake(t.id).visit(b)
        val snap = m.snapshot()
        assertEquals(b, snap.tabs.single { it.id == t.id }.url)
        assertEquals(t.id, snap.selectedTabId)
    }

    @Test
    fun persist_writesSnapshotToStore() {
        val m = manager()
        m.openTab(a)
        m.persist()
        assertEquals(1, store.writes)
        assertEquals(m.ids(), store.stored!!.tabs.map { it.id })
    }

    @Test
    fun restore_afterProcessDeath_restoresTabsSelectionAndUrls() {
        val m1 = manager()
        val t1 = m1.openTab(a)
        m1.fake(t1.id).simulatePageFinished(a)
        m1.fake(t1.id).visit(b)
        val t2 = m1.openTab(c, select = false)
        m1.persist()

        val m2 = manager(f = RecordingSessionFactory())
        assertEquals(m1.ids(), m2.ids())
        assertEquals(t1.id, m2.selectedTabId.value)
        assertEquals(
            b,
            m2.tabs.value
                .single { it.id == t1.id }
                .url,
        )
        assertEquals(
            c,
            m2.tabs.value
                .single { it.id == t2.id }
                .url,
        )
    }

    @Test
    fun restore_selectedTabRestoresEngineHistory() {
        val m1 = manager()
        val t1 = m1.openTab(a)
        m1.fake(t1.id).simulatePageFinished(a)
        m1.fake(t1.id).visit(b)
        m1.persist()

        val m2 = manager(f = RecordingSessionFactory())
        val st = m2.session(t1.id)!!.state.value
        assertEquals(b, st.url)
        assertTrue(st.canGoBack)
    }

    @Test
    fun restore_backgroundTabRestoresOnSelect() {
        val m1 = manager()
        val homeId = m1.ids().single()
        val t1 = m1.openTab(a)
        m1.fake(t1.id).simulatePageFinished(a)
        m1.fake(t1.id).visit(b)
        m1.selectTab(homeId)
        m1.persist()

        val m2 = manager(f = RecordingSessionFactory())
        m2.selectTab(t1.id)
        val st = m2.session(t1.id)!!.state.value
        assertEquals(b, st.url)
        assertTrue(st.canGoBack)
    }

    @Test
    fun restore_newTabIdsDoNotCollideWithRestoredIds() {
        val m1 = manager()
        repeat(5) { m1.openTab("https://site$it.example/") }
        m1.persist()
        val m2 = manager(f = RecordingSessionFactory())
        val restoredIds = m2.ids().toSet()
        repeat(5) { assertFalse(m2.openTab("https://new$it.example/").id in restoredIds) }
        assertEquals(m2.ids().size, m2.ids().toSet().size)
    }

    // --- Tab ẩn danh ---

    @Test
    fun privateTab_hasPrivateFlag() {
        val t = manager().openTab(a, isPrivate = true)
        assertTrue(t.isPrivate)
    }

    @Test
    fun privateTabs_areNeverPersisted() {
        val m = manager()
        val p = m.openTab(a, isPrivate = true)
        m.openTab(b)
        assertTrue(m.snapshot().tabs.none { it.id == p.id || it.url == a })
        m.persist()
        assertTrue(store.stored!!.tabs.none { it.id == p.id || it.url == a })
    }

    @Test
    fun selectedPrivateTab_isNotPersistedAsSelection() {
        val m = manager()
        val homeId = m.ids().single()
        val p = m.openTab(a, isPrivate = true)
        m.persist()
        assertNotEquals(p.id, store.stored!!.selectedTabId)

        val m2 = manager(f = RecordingSessionFactory())
        assertEquals(listOf(homeId), m2.ids())
        assertEquals(homeId, m2.selectedTabId.value)
    }

    // --- Snapshot hỏng ---

    @Test
    fun storeThrowingOnRead_fallsBackToSingleHomeTab() {
        val m = manager(s = InMemoryTabSnapshotStore(failOnRead = true))
        val home = m.tabs.value.single()
        assertEquals(HOME_URL, home.url)
        assertEquals(home.id, m.selectedTabId.value)
    }

    @Test
    fun emptySnapshot_fallsBackToSingleHomeTab() {
        val m = manager(s = InMemoryTabSnapshotStore(TabsSnapshot(emptyList(), null)))
        val home = m.tabs.value.single()
        assertEquals(HOME_URL, home.url)
        assertEquals(home.id, m.selectedTabId.value)
    }

    @Test
    fun snapshotWithUnknownSelectedId_selectsAnExistingTab() {
        val snap = TabsSnapshot(listOf(TabSnapshot("t1", a), TabSnapshot("t2", b)), selectedTabId = "ghost")
        val m = manager(s = InMemoryTabSnapshotStore(snap))
        assertEquals(listOf("t1", "t2"), m.ids())
        assertTrue(m.selectedTabId.value in m.ids())
    }

    @Test
    fun snapshotWithCorruptEngineState_loadsUrlInstead() {
        val snap = TabsSnapshot(listOf(TabSnapshot("t1", a, engineState = byteArrayOf(9, 9, 9))), "t1")
        val m = manager(s = InMemoryTabSnapshotStore(snap))
        assertEquals(
            a,
            m
                .session("t1")!!
                .state.value.url,
        )
    }

    @Test
    fun snapshotWithDuplicateIds_keepsIdsUnique() {
        val snap = TabsSnapshot(listOf(TabSnapshot("t1", a), TabSnapshot("t1", b)), "t1")
        val m = manager(s = InMemoryTabSnapshotStore(snap))
        assertEquals(m.ids().size, m.ids().toSet().size)
        assertTrue(m.selectedTabId.value in m.ids())
    }

    // --- Giới hạn / giải phóng ---

    @Test
    fun maxLiveSessionsBelowOne_isRejected() {
        assertThrows(IllegalArgumentException::class.java, ThrowingRunnable { manager(maxLive = 0) })
    }

    @Test
    fun close_closesAllLiveSessions_andClearsUndo() {
        val m = manager()
        val t1 = m.openTab(a)
        m.openTab(b)
        m.closeTab(t1.id)
        m.close()
        assertTrue(factory.created.all { it.isClosed })
        assertTrue(m.ids().all { m.session(it) == null })
        assertNull(m.undoClose())
    }

    @Test
    fun close_keepsEngineStateInSnapshot() {
        val m = manager()
        val t = m.openTab(a)
        m.fake(t.id).simulatePageFinished(a)
        m.close()
        assertNotNull(
            m
                .snapshot()
                .tabs
                .single { it.id == t.id }
                .engineState,
        )
    }

    @Test
    fun revive_withNonWebUrl_doesNotLoadIt() {
        val snap = TabsSnapshot(listOf(TabSnapshot("t1", "javascript:alert(1)")), "t1")
        val m = manager(s = InMemoryTabSnapshotStore(snap))
        val s = m.fake("t1")
        assertTrue(s.loadedUrls.isEmpty())
        assertEquals(null, s.state.value.error)
    }

    // --- Spec 007 AC12: openTabForWindow ---

    /** [A, B, C] với A được chọn. */
    private fun threeTabs(): Triple<TabManager, Tab, List<Tab>> {
        val m = manager()
        val home = m.tabs.value.single()
        m.fake(home.id).visit(a)
        val tb = m.openTab(b, select = false)
        val tc = m.openTab(c, select = false)
        assertEquals(home.id, m.selectedTabId.value)
        return Triple(m, m.tabs.value.first { it.id == home.id }, listOf(tb, tc))
    }

    @Test
    fun ac12_openTabForWindow_insertsRightAfterOpener_andSelectsIt() {
        val (m, opener, others) = threeTabs()
        val popup = m.openTabForWindow(opener.id)
        assertNotNull(popup)
        assertEquals(listOf(opener.id, popup!!.id, others[0].id, others[1].id), m.ids())
        assertEquals(popup.id, m.selectedTabId.value)
    }

    @Test
    fun ac12_openTabForWindow_newSessionDoesNotLoadAnything() {
        val (m, opener, _) = threeTabs()
        val popup = m.openTabForWindow(opener.id)!!
        val session = m.fake(popup.id)
        assertTrue(session.loadedUrls.isEmpty())
        assertEquals("", session.state.value.url)
        assertFalse(session.state.value.isLoading)
        assertSame(factory.created.last(), session)
    }

    @Test
    fun ac12_openTabForWindow_missingOpener_returnsNull_andKeepsTabs() {
        val (m, opener, _) = threeTabs()
        val before = m.ids()
        val createdBefore = factory.created.size
        assertNull(m.openTabForWindow("missing"))
        assertEquals(before, m.ids())
        assertEquals(opener.id, m.selectedTabId.value)
        assertEquals(createdBefore, factory.created.size)
    }

    // --- Spec 009 AC1: undo giữ lựa chọn ---

    @Test
    fun ac1_undoClose_ofBackgroundTab_keepsCurrentSelection() {
        val (m, ta, others) = threeTabs()
        val (tb, tc) = others
        m.selectTab(tc.id)
        m.closeTab(ta.id)
        val restored = m.undoClose()
        assertEquals(ta.id, restored!!.id)
        assertEquals(listOf(ta.id, tb.id, tc.id), m.ids())
        assertEquals(tc.id, m.selectedTabId.value)
    }

    @Test
    fun ac1_undoClose_ofSelectedTab_selectsItAgain() {
        val (m, ta, others) = threeTabs()
        val (tb, tc) = others
        m.selectTab(tc.id)
        m.closeTab(tc.id)
        assertEquals(tb.id, m.selectedTabId.value)
        m.undoClose()
        assertEquals(listOf(ta.id, tb.id, tc.id), m.ids())
        assertEquals(tc.id, m.selectedTabId.value)
    }

    // --- Spec 009 AC2: undo sau khi đóng tab cuối ---

    /** Một tab A (đã tải [a]) duy nhất. */
    private fun singleTabA(): Pair<TabManager, Tab> {
        val m = manager()
        val home = m.tabs.value.single()
        m.fake(home.id).visit(a)
        m.refresh()
        return m to m.tabs.value.single()
    }

    @Test
    fun ac2_undoAfterClosingLastTab_dropsUnusedHome_andRestoresOnlyA() {
        val (m, ta) = singleTabA()
        m.closeTab(ta.id)
        val h = m.tabs.value.single()
        assertTrue(h.isHome)
        assertNotEquals(ta.id, h.id)
        val homeSession = m.fake(h.id)

        val restored = m.undoClose()

        assertEquals(ta.id, restored!!.id)
        assertEquals(listOf(ta.id), m.ids())
        assertEquals(ta.id, m.selectedTabId.value)
        assertTrue(homeSession.isClosed)
    }

    @Test
    fun ac2_droppedHomeTab_doesNotEnterUndoStack() {
        val (m, ta) = singleTabA()
        m.closeTab(ta.id)
        m.undoClose()
        assertNull(m.undoClose())
        assertEquals(listOf(ta.id), m.ids())
    }

    @Test
    fun ac2_usedHomeTab_isKept_whenUndoing() {
        val (m, ta) = singleTabA()
        m.closeTab(ta.id)
        val h = m.tabs.value.single()
        m.loadUrl(h.id, b)

        m.undoClose()

        assertEquals(listOf(ta.id, h.id), m.ids())
        assertFalse(m.fake(h.id).isClosed)
    }

    // --- Spec 009 AC3: closeAllTabs ---

    @Test
    fun ac3_closeAllTabs_leavesSingleSelectedNewHomeTab_andClosesAllSessions() {
        val (m, ta, others) = threeTabs()
        val old = listOf(ta) + others
        val sessions = old.map { m.fake(it.id) }

        m.closeAllTabs()

        val only = m.tabs.value.single()
        assertTrue(only.isHome)
        assertEquals(only.id, m.selectedTabId.value)
        assertFalse(only.id in old.map { it.id })
        assertTrue(sessions.all { it.isClosed })
    }

    @Test
    fun ac3_undoAfterCloseAll_restoresLeftmostFirst_andRebuildsOriginalOrder() {
        val (m, ta, others) = threeTabs()
        val (tb, tc) = others
        val old = m.ids()
        m.closeAllTabs()
        val homeId =
            m.tabs.value
                .single()
                .id

        val restoredIds = List(3) { m.undoClose()!!.id }

        assertEquals(listOf(ta.id, tb.id, tc.id), restoredIds)
        assertEquals(listOf(ta.id, tb.id, tc.id), m.ids())
        assertFalse(homeId in m.ids())
        assertEquals(old, m.ids())
    }

    @Test
    fun ac3_firstUndoAfterCloseAll_dropsTheUnusedHomeTab() {
        val (m, _, _) = threeTabs()
        m.closeAllTabs()
        val h = m.tabs.value.single()
        val homeSession = m.fake(h.id)
        m.undoClose()
        assertTrue(homeSession.isClosed)
        assertFalse(h.id in m.ids())
    }

    @Test
    fun ac3_closeAllTabs_with12Tabs_restoresOnlyTheTenLeftmost_inOrder() {
        val m = manager()
        m
            .fake(
                m.tabs.value
                    .single()
                    .id,
            ).visit("https://site0.example/")
        repeat(11) { m.openTab("https://site${it + 1}.example/") }
        val original = m.tabs.value.map { it.id }
        assertEquals(12, original.size)

        m.closeAllTabs()
        val undone = generateSequence { m.undoClose() }.toList()

        assertEquals(TabManager.MAX_UNDO, undone.size)
        assertEquals(original.take(10), undone.map { it.id })
        assertEquals(original.take(10), m.ids())
    }

    @Test
    fun ac3_closeAllTabs_includesPrivateTabs_butTheyAreNotUndoable() {
        val (m, ta, _) = threeTabs()
        val p = m.openTab(c, isPrivate = true)
        val privateSession = m.fake(p.id)

        m.closeAllTabs()

        assertTrue(privateSession.isClosed)
        assertTrue(
            m.tabs.value
                .single()
                .isHome,
        )
        // Spec 016 AC9: tab ẩn danh không bao giờ hoàn tác được; tab thường vẫn được.
        val restored = generateSequence { m.undoClose() }.toList()
        assertTrue(restored.none { it.id == p.id || it.isPrivate })
        assertEquals(ta.id, restored.first().id)
    }
}
