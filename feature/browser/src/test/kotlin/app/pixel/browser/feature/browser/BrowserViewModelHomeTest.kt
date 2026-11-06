package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.SecurityState
import app.pixel.browser.core.tabs.HOME_URL
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

/** Spec 010 – AC15–AC17: ViewModel trình duyệt với trang chủ native (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelHomeTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val p = "https://p.com/"

    private fun BrowserFixture.selectedTab() = tabManager.tabs.value.single { it.id == tabManager.selectedTabId.value }

    // --- AC15 ---

    @Test
    fun ac15_freshViewModel_isHome_withoutLoad() {
        val f = BrowserFixture()
        val st = f.state()
        assertTrue(st.isHome)
        assertEquals("", st.url)
        assertEquals("", st.displayHost)
        assertFalse(st.canGoBack)
        assertFalse(st.canGoForward)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertTrue(f.selectedTab().isHome)
    }

    @Test
    fun ac15_newTab_isHome_withoutLoad() {
        val f = BrowserFixture.onPage(p)
        f.viewModel.onNewTab()
        assertEquals(2, f.state().tabCount)
        assertTrue(f.selectedTab().isHome)
        assertEquals(HOME_URL, f.selectedTab().url)
        assertTrue(f.state().isHome)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
    }

    @Test
    fun ac15_homeTabItem_hasEmptyUrlOrHomeUrl_notASearchEngine() {
        val f = BrowserFixture()
        val item = f.state().tabs.single()
        assertTrue(item.url == "" || item.url == HOME_URL)
        assertEquals("", item.title)
    }

    // --- AC16 ---

    @Test
    fun ac16_homeFromPage_showsHome_withBack() {
        val f = BrowserFixture.onPage(p)
        f.viewModel.onHome()
        val st = f.state()
        assertTrue(st.isHome)
        assertTrue(st.canGoBack)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertFalse(f.selected().fake.isClosed)
    }

    @Test
    fun ac16_homeState_resetsPageChrome() {
        val f = BrowserFixture.onPage(p)
        val s = f.selected()
        s.visit("https://q.com/")
        s.goBack()
        s.fake.simulatePageFinished(p) // canGoForward = true
        s.fake.simulateTitle("P")
        s.reload()
        s.fake.simulateProgress(40)
        assertTrue(f.state().canGoForward)
        assertTrue(f.state().isLoading)

        f.viewModel.onHome()

        val st = f.state()
        assertTrue(st.isHome)
        assertEquals("", st.url)
        assertEquals("", st.displayHost)
        assertFalse(st.isLoading)
        assertEquals(0, st.progress)
        assertEquals(SecurityState.Unknown, st.securityState)
        assertFalse(st.canGoForward)
        assertTrue(st.canGoBack)
        assertFalse(s.state.value.isLoading)
    }

    @Test
    fun ac16_backFromHome_returnsToPage_withoutGoBack() {
        val f = BrowserFixture.onPage(p)
        f.selected().visit("https://q.com/")
        f.selected().resetCalls()
        f.viewModel.onHome()
        f.viewModel.onBack()
        val st = f.state()
        assertFalse(st.isHome)
        assertEquals("https://q.com/", st.url)
        assertEquals(0, f.selected().goBackCalls)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertTrue(st.canGoBack) // lịch sử engine q → p vẫn còn
    }

    @Test
    fun ac16_homeWhileAtHome_changesNothing() {
        val f = BrowserFixture.onPage(p)
        f.viewModel.onHome()
        val before = f.state()
        val tabsBefore = f.tabManager.tabs.value
        f.selected().resetCalls()
        f.viewModel.onHome()
        assertEquals(before, f.state())
        assertEquals(tabsBefore, f.tabManager.tabs.value)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertEquals(0, f.selected().stopLoadingCalls)
    }

    @Test
    fun ac16_homeOnInitialHomeTab_isNoOp() {
        val f = BrowserFixture()
        val before = f.state()
        f.viewModel.onHome()
        assertEquals(before, f.state())
        assertFalse(f.state().canGoBack)
    }

    // --- AC17 ---

    @Test
    fun ac17_submitFromHome_leavesHome_andLoadsNormalizedUrl() {
        val f = BrowserFixture()
        f.viewModel.onStartEditing()
        f.viewModel.onSubmit("example.com")
        assertFalse(f.state().isHome)
        assertEquals(listOf("https://example.com"), f.selected().loadUrlCalls)
        assertTrue(f.selectedTab().backToHome)
    }

    @Test
    fun ac17_suggestionFromHome_leavesHome() {
        val f = BrowserFixture()
        f.viewModel.onStartEditing()
        f.viewModel.onQueryChange("example.com")
        f.viewModel.onSuggestionClick(f.state().suggestions[0])
        assertFalse(f.state().isHome)
        assertEquals(listOf("https://example.com"), f.selected().loadUrlCalls)
    }

    @Test
    fun ac17_openUrl_loadsExactUrl_andLeavesHome() {
        val f = BrowserFixture()
        f.viewModel.onOpenUrl("https://a.com/")
        assertFalse(f.state().isHome)
        assertEquals(listOf("https://a.com/"), f.selected().loadUrlCalls)
        assertEquals(1, f.state().tabCount)
    }

    @Test
    fun ac17_openUrl_nonWebUrls_areIgnored() {
        val f = BrowserFixture()
        f.viewModel.onOpenUrl("javascript:x")
        f.viewModel.onOpenUrl(HOME_URL)
        f.viewModel.onOpenUrl("file:///sdcard/a.html")
        f.viewModel.onOpenUrl("")
        assertTrue(f.state().isHome)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
    }

    @Test
    fun ac17_firstPageFromHome_canGoBack_andBackReturnsHome() {
        val f = BrowserFixture()
        f.viewModel.onOpenUrl("https://a.com/")
        f.selected().fake.simulatePageFinished("https://a.com/")
        assertFalse(
            f
                .selected()
                .state.value.canGoBack,
        )
        assertTrue(f.state().canGoBack)
        f.viewModel.onBack()
        assertTrue(f.state().isHome)
        assertEquals(0, f.selected().goBackCalls)
        assertFalse(f.state().canGoBack)
    }

    @Test
    fun ac17_backWithEngineHistory_goesBackInEngine() {
        val f = BrowserFixture()
        f.viewModel.onOpenUrl("https://a.com/")
        f.selected().fake.simulatePageFinished("https://a.com/")
        f.viewModel.onOpenUrl("https://b.com/")
        f.selected().fake.simulatePageFinished("https://b.com/")
        f.viewModel.onBack()
        assertEquals(1, f.selected().goBackCalls)
        assertFalse(f.state().isHome)
    }

    @Test
    fun ac17_openUrlOnPage_navigatesInSameTab() {
        val f = BrowserFixture.onPage(p)
        f.viewModel.onOpenUrl("https://a.com/")
        assertEquals(listOf("https://a.com/"), f.selected().loadUrlCalls)
        assertFalse(f.selectedTab().backToHome)
    }
}
