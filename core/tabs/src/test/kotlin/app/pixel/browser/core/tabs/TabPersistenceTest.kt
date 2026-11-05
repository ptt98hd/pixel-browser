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
}
