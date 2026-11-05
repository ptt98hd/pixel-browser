package app.pixel.browser.feature.browser

import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.intent.IncomingRequest
import app.pixel.browser.feature.browser.intent.RejectReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 006 – AC13: intent vào mở tab mới hoặc tái dùng tab home chưa dùng (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelIntentTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** TabManager khôi phục với một tab A (`https://a.com`) được chọn và đã tải xong. */
    private fun withTabA(): BrowserFixture {
        val store =
            InMemoryStore().apply {
                stored = TabsSnapshot(listOf(TabSnapshot(id = "a", url = "https://a.com", title = "A")), "a")
            }
        val f = BrowserFixture(store)
        f.tabManager
            .spy("a")
            .fake
            .simulatePageFinished("https://a.com")
        f.tabManager.spy("a").resetCalls()
        return f
    }

    @Test
    fun ac13_openUrl_opensNewSelectedTabAtEnd_andKeepsTabA() {
        val f = withTabA()
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://b.com"))

        val tabs = f.tabManager.tabs.value
        assertEquals(2, tabs.size)
        assertEquals("a", tabs[0].id)
        val newId = tabs[1].id
        assertEquals(newId, f.tabManager.selectedTabId.value)
        assertEquals(
            "https://b.com",
            f.tabManager
                .spy(newId)
                .loadUrlCalls
                .last(),
        )
        assertEquals("https://b.com", f.state().url)
        assertEquals(2, f.state().tabCount)
        // Tab A giữ nguyên.
        assertTrue(
            f.tabManager
                .spy("a")
                .loadUrlCalls
                .isEmpty(),
        )
        assertEquals(
            "https://a.com",
            f.tabManager
                .spy("a")
                .state.value.url,
        )
    }

    @Test
    fun ac13_openUrl_twice_opensTwoTabs() {
        val f = withTabA()
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://b.com"))
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://c.com"))
        assertEquals(3, f.tabManager.tabs.value.size)
        assertEquals("https://c.com", f.selected().loadUrlCalls.last())
    }

    @Test
    fun ac13_unusedHomeTab_isReused_beforeHomePageFinished() {
        val f = BrowserFixture()
        val homeId = f.tabManager.selectedTabId.value
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://b.com"))
        assertEquals(1, f.tabManager.tabs.value.size)
        assertEquals(homeId, f.tabManager.selectedTabId.value)
        assertEquals("https://b.com", f.selected().loadUrlCalls.last())
    }

    @Test
    fun ac13_unusedHomeTab_isReused_afterHomePageFinished() {
        val f = BrowserFixture()
        val homeId = f.tabManager.selectedTabId.value
        f.selected().fake.simulatePageFinished(f.homeUrl)
        assertFalse(
            f
                .selected()
                .state.value.canGoBack,
        )
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://b.com"))
        assertEquals(1, f.tabManager.tabs.value.size)
        assertEquals(homeId, f.tabManager.selectedTabId.value)
        assertEquals("https://b.com", f.selected().loadUrlCalls.last())
    }

    @Test
    fun ac13_homeTabWithHistory_isNotReused() {
        val f = BrowserFixture()
        val homeId = f.tabManager.selectedTabId.value
        f.selected().fake.simulatePageFinished(f.homeUrl)
        f.selected().visit("https://x.com")
        assertTrue(
            f
                .selected()
                .state.value.canGoBack,
        )
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://b.com"))
        assertEquals(2, f.tabManager.tabs.value.size)
        assertNotEquals(homeId, f.tabManager.selectedTabId.value)
        assertEquals(
            "https://x.com",
            f.tabManager
                .spy(homeId!!)
                .state.value.url,
        )
    }

    @Test
    fun ac13_search_isHandledLikeOpenUrlOfSearchUrl() {
        val f = withTabA()
        val url = "https://www.google.com/search?q=hello"
        f.viewModel.onIncomingIntent(IncomingRequest.Search("hello", url))
        assertEquals(2, f.tabManager.tabs.value.size)
        assertEquals(url, f.selected().loadUrlCalls.last())
        assertTrue(
            f.tabManager
                .spy("a")
                .loadUrlCalls
                .isEmpty(),
        )
    }

    @Test
    fun ac13_resume_changesNothing() {
        val f = withTabA()
        val before = f.tabManager.tabs.value
        f.viewModel.onIncomingIntent(IncomingRequest.Resume)
        assertEquals(before, f.tabManager.tabs.value)
        assertEquals("a", f.tabManager.selectedTabId.value)
        assertTrue(
            f.tabManager
                .spy("a")
                .loadUrlCalls
                .isEmpty(),
        )
        assertNull(f.state().message)
    }

    @Test
    fun ac13_rejected_changesNoTab_andShowsMessage() {
        val f = withTabA()
        val before = f.tabManager.tabs.value
        for (reason in RejectReason.entries) {
            f.viewModel.onIncomingIntent(IncomingRequest.Rejected(reason))
            assertEquals(before, f.tabManager.tabs.value)
            assertEquals("a", f.tabManager.selectedTabId.value)
            assertTrue(
                f.tabManager
                    .spy("a")
                    .loadUrlCalls
                    .isEmpty(),
            )
            assertNotNull(reason.name, f.state().message)
            f.viewModel.onMessageShown()
        }
    }

    @Test
    fun edge_incomingIntent_whileEditing_exitsEditingBeforeOpeningTab() {
        val f = withTabA()
        f.viewModel.onStartEditing()
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://b.com"))
        assertFalse(f.state().isEditingUrl)
        assertEquals(2, f.state().tabCount)
    }
}
