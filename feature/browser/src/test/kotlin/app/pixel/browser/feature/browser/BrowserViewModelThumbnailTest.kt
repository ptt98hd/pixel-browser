package app.pixel.browser.feature.browser

import app.pixel.browser.core.tabs.HOME_URL
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 009 AC8–AC9 – ViewModel gọi `TabThumbnailRepository` đúng lúc (JVM, `FakeTabThumbnailRepository`). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelThumbnailTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val a = "https://a.com/"
    private val b = "https://b.com/"
    private val thumbs = FakeTabThumbnailRepository()

    /** Tab "page" (đã tải [a], được chọn) và một tab nền B đã tải [b]; lời gọi thumbnail đã được xóa. */
    private fun pageWithBackgroundTab(): Pair<BrowserFixture, String> {
        val f = BrowserFixture.onPage(a, thumbnails = thumbs)
        val tb = f.tabManager.openTab(b, select = false)
        f.tabManager
            .spy(tb.id)
            .fake
            .simulatePageFinished(b)
        thumbs.calls.clear()
        return f to tb.id
    }

    // --- AC8: chụp khi rời tab ---

    @Test
    fun ac8_selectOtherTab_capturesCurrentTab_whileItIsStillSelected_thenSelectsOther() {
        val (f, bId) = pageWithBackgroundTab()
        val sessionA = f.tabManager.session(BrowserFixture.PAGE_TAB_ID)

        f.viewModel.onSelectTab(bId)

        val capture = thumbs.captures.single()
        assertEquals(BrowserFixture.PAGE_TAB_ID, capture.tabId)
        assertSame(sessionA, capture.session)
        assertTrue(capture.persist)
        assertEquals(BrowserFixture.PAGE_TAB_ID, capture.selectedAtCall)
        assertEquals(bId, f.tabManager.selectedTabId.value)
    }

    @Test
    fun ac8_newTab_capturesCurrentTab() {
        val (f, _) = pageWithBackgroundTab()
        val sessionA = f.tabManager.session(BrowserFixture.PAGE_TAB_ID)

        f.viewModel.onNewTab()

        val capture = thumbs.captures.single()
        assertEquals(BrowserFixture.PAGE_TAB_ID, capture.tabId)
        assertSame(sessionA, capture.session)
        assertEquals(BrowserFixture.PAGE_TAB_ID, capture.selectedAtCall)
    }

    @Test
    fun ac8_openTabSwitcher_capturesCurrentTab() {
        val (f, _) = pageWithBackgroundTab()
        val sessionA = f.tabManager.session(BrowserFixture.PAGE_TAB_ID)

        f.viewModel.onOpenTabSwitcher()

        val capture = thumbs.captures.single()
        assertEquals(BrowserFixture.PAGE_TAB_ID, capture.tabId)
        assertSame(sessionA, capture.session)
        assertTrue(capture.persist)
        assertEquals(BrowserFixture.PAGE_TAB_ID, capture.selectedAtCall)
    }

    @Test
    fun ac8_homeTab_isNeverCaptured() {
        val f = BrowserFixture(thumbnails = thumbs)
        assertTrue(f.state().isHome)
        val other = f.tabManager.openTab(b, select = false)

        f.viewModel.onOpenTabSwitcher()
        f.viewModel.onNewTab()
        f.viewModel.onSelectTab(other.id)

        assertTrue(thumbs.captures.isEmpty())
    }

    @Test
    fun ac8_privateTab_isCapturedWithPersistFalse() {
        val f = BrowserFixture.onPage(a, thumbnails = thumbs)
        val priv = f.tabManager.openTab(b, isPrivate = true)
        thumbs.calls.clear()

        f.viewModel.onOpenTabSwitcher()

        val capture = thumbs.captures.single()
        assertEquals(priv.id, capture.tabId)
        assertFalse(capture.persist)
    }

    @Test
    fun ac8_selectingTheAlreadySelectedTab_doesNotCapture() {
        val (f, _) = pageWithBackgroundTab()
        f.viewModel.onSelectTab(BrowserFixture.PAGE_TAB_ID)
        assertTrue(thumbs.captures.isEmpty())
    }

    // --- AC9: đóng / undo / close all / khởi tạo ---

    @Test
    fun ac9_closeTab_removesItsThumbnail() {
        val (f, bId) = pageWithBackgroundTab()
        f.viewModel.onCloseTab(bId)
        assertEquals(listOf(bId), thumbs.removed)
    }

    @Test
    fun ac9_undoCloseTab_restoresTabViaTabManager_withoutTouchingSelection() {
        val (f, bId) = pageWithBackgroundTab()
        f.viewModel.onCloseTab(bId)

        f.viewModel.onUndoCloseTab()

        assertEquals(listOf(BrowserFixture.PAGE_TAB_ID, bId), f.state().tabs.map { it.id })
        assertEquals(BrowserFixture.PAGE_TAB_ID, f.state().selectedTabId)
    }

    @Test
    fun ac9_closeAllTabs_leavesOneHomeTab_andRemovesEveryOldThumbnail() {
        val (f, bId) = pageWithBackgroundTab()
        val old = listOf(BrowserFixture.PAGE_TAB_ID, bId)

        f.viewModel.onCloseAllTabs()

        val state = f.state()
        assertEquals(1, state.tabCount)
        assertTrue(state.isHome)
        assertTrue(state.tabs.single().id !in old)
        assertEquals(old.toSet(), thumbs.removed.toSet())
        assertEquals(old.size, thumbs.removed.size)
    }

    @Test
    fun ac9_closeAllTabs_thenUndo_restoresLeftmostTab() {
        val (f, _) = pageWithBackgroundTab()
        f.viewModel.onCloseAllTabs()
        f.viewModel.onUndoCloseTab()
        assertEquals(
            BrowserFixture.PAGE_TAB_ID,
            f
                .state()
                .tabs
                .first()
                .id,
        )
    }

    @Test
    fun ac9_newViewModel_retainsOnlyPersistedTabIds_exactlyOnce() {
        val store =
            InMemoryStore().apply {
                stored = TabsSnapshot(listOf(TabSnapshot("A", a), TabSnapshot("B", b)), "A")
            }
        val f = BrowserFixture(store, thumbnails = thumbs)

        assertEquals(1, thumbs.retains.size)
        assertEquals(setOf("A", "B"), thumbs.retains.single().tabIds)
        // Thêm thao tác khác không gọi retainOnly lần nữa.
        f.viewModel.onNewTab()
        assertEquals(1, thumbs.retains.size)
    }

    @Test
    fun ac9_retainOnly_excludesPrivateTabs() {
        val store =
            InMemoryStore().apply {
                stored = TabsSnapshot(listOf(TabSnapshot("A", a), TabSnapshot("B", b)), "A")
            }
        val factory = SpySessionFactory()
        val manager = TabManager(factory, store)
        val priv = manager.openTab("https://p.com/", isPrivate = true)

        BrowserViewModel(manager, thumbnails = thumbs)

        val retained = thumbs.retains.single().tabIds
        assertEquals(setOf("A", "B"), retained)
        assertFalse(priv.id in retained)
    }

    @Test
    fun ac9_tabItems_exposeIsHomeAndIsPrivate() {
        val f = BrowserFixture.onPage(a, thumbnails = thumbs)
        val home = f.tabManager.openTab(HOME_URL, select = false)
        val priv = f.tabManager.openTab(b, select = false, isPrivate = true)

        val items = f.state().tabs.associateBy { it.id }
        assertFalse(items.getValue(BrowserFixture.PAGE_TAB_ID).isHome)
        assertFalse(items.getValue(BrowserFixture.PAGE_TAB_ID).isPrivate)
        assertTrue(items.getValue(home.id).isHome)
        assertFalse(items.getValue(home.id).isPrivate)
        assertFalse(items.getValue(priv.id).isHome)
        assertTrue(items.getValue(priv.id).isPrivate)
    }

    @Test
    fun ac9_defaultRepository_isNoOp() {
        // Không truyền `thumbnails`: mọi thao tác vẫn chạy bình thường.
        val f = BrowserFixture.onPage(a)
        f.viewModel.onNewTab()
        assertEquals(2, f.state().tabCount)
    }
}
