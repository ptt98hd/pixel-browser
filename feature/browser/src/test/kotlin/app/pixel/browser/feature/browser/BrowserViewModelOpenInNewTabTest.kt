package app.pixel.browser.feature.browser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/** Spec 012 AC17 (phần ViewModel) – `onOpenUrlInNewTab`. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelOpenInNewTabTest {
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

    @Test
    fun validUrl_opensNewSelectedTab_afterCapturingCurrentThumbnail() {
        val f = BrowserFixture.onPage(a, thumbnails = thumbs)
        thumbs.calls.clear()
        f.viewModel.onOpenUrlInNewTab(b)
        assertEquals(2, f.state().tabCount)
        assertNotEquals(BrowserFixture.PAGE_TAB_ID, f.tabManager.selectedTabId.value)
        assertEquals(listOf(b), f.selected().loadUrlCalls)
        assertEquals(BrowserFixture.PAGE_TAB_ID, thumbs.captures.single().tabId)
        assertEquals(BrowserFixture.PAGE_TAB_ID, thumbs.captures.single().selectedAtCall)
    }

    @Test
    fun invalidUrl_isIgnored() {
        val f = BrowserFixture.onPage(a)
        listOf("javascript:alert(1)", "file:///x", "about:blank", "not a url", "").forEach {
            f.viewModel.onOpenUrlInNewTab(it)
        }
        assertEquals(1, f.state().tabCount)
    }
}
