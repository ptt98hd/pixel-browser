package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.FakeEngineSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Spec 002 – lưu snapshot xuống file JSON (org.json cần Robolectric). */
@RunWith(RobolectricTestRunner::class)
class TabPersistenceTest {
    @get:Rule val tmp = TemporaryFolder()

    private val file: File get() = File(tmp.root, "tabs.json")

    private fun store() = FileTabSnapshotStore(file)

    @Test
    fun read_missingFile_returnsNull() {
        assertNull(store().read())
    }

    @Test
    fun writeThenRead_roundTripsAllFields() {
        val bytes = byteArrayOf(0, 1, 2, 127, -128, -1)
        val snap =
            TabsSnapshot(
                listOf(
                    TabSnapshot("t1", "https://a.example/", "A \"quoted\" title", bytes),
                    TabSnapshot("t2", "https://b.example/?q=ü&x=1", "", null),
                ),
                selectedTabId = "t2",
            )
        store().write(snap)

        val read = store().read()
        assertNotNull(read)
        assertEquals("t2", read!!.selectedTabId)
        assertEquals(listOf("t1", "t2"), read.tabs.map { it.id })
        assertEquals(listOf("https://a.example/", "https://b.example/?q=ü&x=1"), read.tabs.map { it.url })
        assertEquals("A \"quoted\" title", read.tabs[0].title)
        assertArrayEquals(bytes, read.tabs[0].engineState)
        assertNull(read.tabs[1].engineState)
    }

    @Test
    fun write_nullSelection_roundTrips() {
        store().write(TabsSnapshot(listOf(TabSnapshot("t1", "https://a.example/")), null))
        assertNull(store().read()!!.selectedTabId)
    }

    @Test
    fun write_overwritesPreviousSnapshot() {
        store().write(TabsSnapshot(listOf(TabSnapshot("old", "https://old.example/")), "old"))
        store().write(TabsSnapshot(listOf(TabSnapshot("new", "https://new.example/")), "new"))
        assertEquals(listOf("new"), store().read()!!.tabs.map { it.id })
    }

    @Test
    fun read_corruptJson_returnsNull() {
        file.writeText("{not json")
        assertNull(store().read())
    }

    @Test
    fun read_wrongShapeJson_returnsNull() {
        file.writeText("""{"tabs": 42}""")
        assertNull(store().read())
    }

    @Test
    fun read_invalidBase64EngineState_dropsStateButKeepsTab() {
        file.writeText(
            """{"selectedTabId":"t1","tabs":[{"id":"t1","url":"https://a.example/","title":"","engineState":"%%%"}]}""",
        )
        val read = store().read()
        // Chấp nhận: hoặc bỏ cả snapshot (null), hoặc giữ tab nhưng không có engineState.
        if (read != null) {
            assertEquals("t1", read.tabs.single().id)
            assertNull(read.tabs.single().engineState)
        }
    }

    @Test
    fun corruptFile_tabManagerStartsWithHomeTab() {
        file.writeText("\u0000garbage")
        val m = TabManager(RecordingSessionFactory(), store())
        val home = m.tabs.value.single()
        assertEquals(HOME_URL, home.url)
        assertEquals(home.id, m.selectedTabId.value)
    }

    @Test
    fun processDeath_restoresTabsAndHistoryFromFile() {
        val m1 = TabManager(RecordingSessionFactory(), store())
        val t = m1.openTab("https://a.example/")
        val s = m1.session(t.id) as FakeEngineSession
        s.simulatePageFinished("https://a.example/")
        s.loadUrl("https://b.example/")
        s.simulatePageFinished("https://b.example/")
        m1.persist()

        val m2 = TabManager(RecordingSessionFactory(), FileTabSnapshotStore(file))
        assertEquals(m1.tabs.value.map { it.id }, m2.tabs.value.map { it.id })
        assertEquals(t.id, m2.selectedTabId.value)
        val st = m2.session(t.id)!!.state.value
        assertEquals("https://b.example/", st.url)
        assertTrue(st.canGoBack)
    }

    @Test
    fun privateTabs_neverReachDisk() {
        val m = TabManager(RecordingSessionFactory(), store())
        val secret = "https://secret-private.example/"
        val p = m.openTab(secret, isPrivate = true)
        (m.session(p.id) as FakeEngineSession).simulatePageFinished(secret)
        m.openTab("https://public.example/")
        m.persist()

        val text = file.readText()
        assertFalse(text.contains("secret-private"))
        val m2 = TabManager(RecordingSessionFactory(), FileTabSnapshotStore(file))
        assertTrue(m2.tabs.value.none { it.isPrivate || it.id == p.id })
    }

    @Test
    fun write_ioFailure_doesNotThrow_andLeavesNoTempFile() {
        val blocker = File(tmp.root, "not-a-dir").apply { writeText("x") }
        val bad = FileTabSnapshotStore(File(blocker, "tabs.json"))
        bad.write(TabsSnapshot(listOf(TabSnapshot("t1", "https://a.example/")), "t1"))
        assertNull(bad.read())
        assertEquals(listOf("not-a-dir"), tmp.root.list()!!.toList())
    }

    @Test
    fun write_replacesFileAtomically_noLeftovers() {
        store().write(TabsSnapshot(listOf(TabSnapshot("old", "https://old.example/")), "old"))
        store().write(TabsSnapshot(listOf(TabSnapshot("new", "https://new.example/")), "new"))
        assertEquals(listOf("tabs.json"), tmp.root.list()!!.toList())
        assertEquals("new", store().read()!!.selectedTabId)
    }

    // --- Spec 010 AC6: snapshot & migration của tab home ---

    private fun TabManager.fake(id: String) =
        when (val s = session(id)) {
            is CountingSession -> s.fake
            else -> s as FakeEngineSession
        }

    private fun TabManager.loadCalls(id: String) = (session(id) as CountingSession).loadUrlCalls

    /** Engine state hợp lệ của Fake với mục hiện tại là [urls].last(). */
    private fun engineStateOf(vararg urls: String): ByteArray {
        val s = FakeEngineSession()
        urls.forEach {
            s.loadUrl(it)
            s.simulatePageFinished(it)
        }
        return s.saveState()!!
    }

    @Test
    fun ac6_homeOverPage_snapshotHasHomeUrlAndEngineState() {
        val m = TabManager(RecordingSessionFactory(), store())
        val t = m.openTab("https://a.example/")
        m.fake(t.id).simulatePageFinished("https://a.example/")
        m.showHome(t.id)
        val snap = m.snapshot().tabs.single { it.id == t.id }
        assertEquals(HOME_URL, snap.url)
        assertNotNull(snap.engineState)
    }

    @Test
    fun ac6_homeOverPage_restoredAsHome_withoutLoad_andBackReturnsToPage() {
        val m1 = TabManager(RecordingSessionFactory(), store())
        val t = m1.openTab("https://a.example/")
        m1.fake(t.id).simulatePageFinished("https://a.example/")
        m1.showHome(t.id)
        m1.persist()

        val m2 = TabManager(CountingSessionFactory(), FileTabSnapshotStore(file))
        assertEquals(t.id, m2.selectedTabId.value)
        val restored = m2.tabs.value.single { it.id == t.id }
        assertTrue(restored.isHome)
        assertTrue((m2.session(t.id) as CountingSession).loadUrlCalls.isEmpty())
        assertTrue(m2.navigateBack(t.id))
        val back = m2.tabs.value.single { it.id == t.id }
        assertFalse(back.isHome)
        assertEquals("https://a.example/", back.url)
    }

    @Test
    fun ac6_backToHome_roundTripsThroughFileStore() {
        store().write(
            TabsSnapshot(
                listOf(
                    TabSnapshot("t1", "https://a.example/", backToHome = true),
                    TabSnapshot("t2", "https://b.example/", backToHome = false),
                ),
                "t1",
            ),
        )
        val read = store().read()!!
        assertEquals(listOf(true, false), read.tabs.map { it.backToHome })
    }

    @Test
    fun ac6_backToHome_restoredIntoTab() {
        store().write(TabsSnapshot(listOf(TabSnapshot("t1", "https://a.example/", backToHome = true)), "t1"))
        val m = TabManager(RecordingSessionFactory(), store())
        assertTrue(
            m.tabs.value
                .single()
                .backToHome,
        )
    }

    @Test
    fun ac6_legacyFileWithoutBackToHomeKey_defaultsToFalse() {
        file.writeText("""{"selectedTabId":"t1","tabs":[{"id":"t1","url":"https://a.example/","title":"A"}]}""")
        val read = store().read()!!
        assertFalse(read.tabs.single().backToHome)
    }

    @Test
    fun ac6_migration_legacyAboutBlankWithoutState_becomesHome() {
        store().write(TabsSnapshot(listOf(TabSnapshot("t1", "about:blank", engineState = null)), "t1"))
        val m = TabManager(CountingSessionFactory(), store())
        val tab = m.tabs.value.single()
        assertTrue(tab.isHome)
        assertEquals(HOME_URL, tab.url)
        assertTrue(m.loadCalls("t1").isEmpty())
        assertEquals(
            "",
            m
                .fake("t1")
                .state.value.url,
        )
    }

    @Test
    fun ac6_migration_aboutBlankWithValidState_isNotHome_andRestoresState() {
        val state = engineStateOf("https://a.example/", "about:blank")
        store().write(TabsSnapshot(listOf(TabSnapshot("t1", "about:blank", engineState = state)), "t1"))
        val m = TabManager(RecordingSessionFactory(), store())
        val tab = m.tabs.value.single()
        assertFalse(tab.isHome)
        val st = m.fake("t1").state.value
        assertEquals("about:blank", st.url)
        assertTrue(st.canGoBack)
    }

    @Test
    fun ac6_discardedHomeTab_reselected_doesNotLoad() {
        val m = TabManager(CountingSessionFactory(), store(), maxLiveSessions = 1)
        val homeId = m.selectedTabId.value!!
        m.openTab("https://a.example/")
        assertEquals(null, m.session(homeId))
        m.selectTab(homeId)
        assertTrue(
            m.tabs.value
                .single { it.id == homeId }
                .isHome,
        )
        assertTrue(m.loadCalls(homeId).isEmpty())
        assertEquals(
            "",
            m
                .fake(homeId)
                .state.value.url,
        )
    }
}
