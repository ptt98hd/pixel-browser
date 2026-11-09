package app.pixel.browser.feature.browser

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 008 AC13 – `BrowserViewModel.onSelectAdjacentTab` (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelGestureTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val thumbs = FakeTabThumbnailRepository()

    /** Tab [a, b, c] (đều là trang web), [selected] đang chọn; lời gọi thumbnail đã xóa. */
    private fun threeTabs(selected: String): BrowserFixture {
        val store =
            InMemoryStore().apply {
                stored =
                    TabsSnapshot(
                        listOf(
                            TabSnapshot("a", "https://a.com/"),
                            TabSnapshot("b", "https://b.com/"),
                            TabSnapshot("c", "https://c.com/"),
                        ),
                        selected,
                    )
            }
        return BrowserFixture(store, thumbnails = thumbs).also { thumbs.calls.clear() }
    }

    private fun BrowserFixture.selectedId() = tabManager.selectedTabId.value

    @Test
    fun ac13_next_selectsNextTab() {
        val f = threeTabs("b")
        f.viewModel.onSelectAdjacentTab(+1)
        assertEquals("c", f.selectedId())
        assertEquals("c", f.state().selectedTabId)
    }

    @Test
    fun ac13_previous_selectsPreviousTab() {
        val f = threeTabs("b")
        f.viewModel.onSelectAdjacentTab(-1)
        assertEquals("a", f.selectedId())
    }

    @Test
    fun ac13_capturesLeavingTab_whileStillSelected_beforeSelectingNext() {
        val f = threeTabs("b")
        f.viewModel.onSelectAdjacentTab(+1)
        val capture = thumbs.captures.single()
        assertEquals("b", capture.tabId)
        assertEquals("b", capture.selectedAtCall)
        assertEquals("c", f.selectedId())
    }

    @Test
    fun ac13_leavesUrlEditing() {
        val f = threeTabs("b")
        f.viewModel.onStartEditing()
        assertTrue(f.state().isEditingUrl)
        f.viewModel.onSelectAdjacentTab(+1)
        assertFalse(f.state().isEditingUrl)
    }

    @Test
    fun ac13_nextOnLastTab_isNoOp_withoutCapture() {
        val f = threeTabs("c")
        f.viewModel.onSelectAdjacentTab(+1)
        assertEquals("c", f.selectedId())
        assertTrue(thumbs.captures.isEmpty())
    }

    @Test
    fun ac13_previousOnFirstTab_isNoOp_withoutCapture() {
        val f = threeTabs("a")
        f.viewModel.onSelectAdjacentTab(-1)
        assertEquals("a", f.selectedId())
        assertTrue(thumbs.captures.isEmpty())
    }
}
