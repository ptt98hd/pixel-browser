package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.ScrollCommand
import app.pixel.browser.core.engine.SecurityState
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.core.omnibox.searchUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 005 – ViewModel trên JVM thuần: TabManager thật + SpySession(FakeEngineSession). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    private lateinit var f: BrowserFixture
    private val vm get() = f.viewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- AC1 (phần ViewModel) ---

    @Test
    fun init_emptyTabManager_selectedTabIsHome_withoutLoad() {
        // Spec 010: tab home không tải trang của search engine.
        assertTrue(f.state().isHome)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertEquals("", f.state().url)
        assertEquals(1, f.state().tabCount)
        assertEquals(f.tabManager.selectedTabId.value, f.state().selectedTabId)
    }

    // --- AC3 ---

    @Test
    fun selectTab_updatesUrlHostAndHistory_withoutClosingPreviousSession() {
        val a = f.tabManager.openTab("https://a.com/start")
        f.tabManager.spy(a.id).visit("https://a.com/start")
        f.tabManager.spy(a.id).visit("https://a.com")
        val b = f.tabManager.openTab("https://b.com")
        f.tabManager
            .spy(b.id)
            .fake
            .simulatePageFinished("https://b.com")
        vm.onSelectTab(a.id)
        assertEquals("a.com", f.state().displayHost)
        assertTrue(f.state().canGoBack)

        vm.onSelectTab(b.id)

        assertEquals(b.id, f.state().selectedTabId)
        assertEquals("https://b.com", f.state().url)
        assertEquals("b.com", f.state().displayHost)
        assertFalse(f.state().canGoBack)
        assertFalse(
            f.tabManager
                .spy(a.id)
                .fake.isClosed,
        )
    }

    @Test
    fun selectedSessionStateChanges_areReflectedLive() {
        f = BrowserFixture.onPage("https://start.example/")
        val s = f.selected()
        s.loadUrl("https://www.example.com/path?q=1")
        s.fake.simulateProgress(70)
        assertTrue(f.state().isLoading)
        assertEquals(70, f.state().progress)
        assertEquals("example.com", f.state().displayHost)
        s.fake.simulateTitle("Example")
        s.fake.simulatePageFinished("https://www.example.com/path?q=1")
        assertFalse(f.state().isLoading)
        assertEquals("Example", f.state().title)
        assertEquals(SecurityState.Secure, f.state().securityState)
    }

    @Test
    fun backgroundSessionChanges_doNotAffectChrome() {
        val first = f.tabManager.selectedTabId.value!!
        f.tabManager.openTab("https://b.com")
        f.tabManager.spy(first).loadUrl("https://other.com")
        assertEquals("https://b.com", f.state().url)
    }

    // --- AC10 / AC11 ---

    @Test
    fun submit_url_loadsNormalizedUrlOnce_andExitsEditing() {
        vm.onStartEditing()
        f.selected().resetCalls()
        vm.onSubmit("example.com")
        assertEquals(listOf("https://example.com"), f.selected().loadUrlCalls)
        assertFalse(f.state().isEditingUrl)
    }

    @Test
    fun submit_text_loadsSearchUrl() {
        vm.onStartEditing()
        f.selected().resetCalls()
        vm.onSubmit("hello world")
        assertEquals(listOf(vm.searchEngine.searchUrl("hello world")), f.selected().loadUrlCalls)
        assertFalse(f.state().isEditingUrl)
    }

    @Test
    fun submit_blockedScheme_doesNotLoad_setsMessage_staysEditing() {
        vm.onStartEditing()
        f.selected().resetCalls()
        vm.onSubmit("javascript:alert(1)")
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertNotNull(f.state().message)
        assertTrue(f.state().isEditingUrl)
    }

    @Test
    fun messageShown_clearsMessage() {
        vm.onStartEditing()
        vm.onSubmit("javascript:alert(1)")
        vm.onMessageShown()
        assertNull(f.state().message)
    }

    @Test
    fun submit_empty_doesNothing_staysEditing_noMessage() {
        vm.onStartEditing()
        f.selected().resetCalls()
        vm.onSubmit("   ")
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertTrue(f.state().isEditingUrl)
        assertNull(f.state().message)
    }

    // --- AC12 / AC13 / AC20 ---

    @Test
    fun queryChange_producesGoToAndSearchSuggestions() {
        vm.onStartEditing()
        vm.onQueryChange("example.com")
        val s = f.state().suggestions
        assertEquals(listOf(SuggestionKind.GoTo, SuggestionKind.Search), s.map { it.kind })
        assertEquals("https://example.com", s[0].url)
        assertEquals(vm.searchEngine.searchUrl("example.com"), s[1].url)
    }

    @Test
    fun suggestionClick_loadsItsUrl_andExitsEditing() {
        vm.onStartEditing()
        vm.onQueryChange("example.com")
        f.selected().resetCalls()
        vm.onSuggestionClick(f.state().suggestions[1])
        assertEquals(listOf(vm.searchEngine.searchUrl("example.com")), f.selected().loadUrlCalls)
        assertFalse(f.state().isEditingUrl)
    }

    @Test
    fun cancelEditing_exitsWithoutLoading_andClearsSuggestions() {
        f = BrowserFixture.onPage("https://www.google.com/")
        vm.onStartEditing()
        vm.onQueryChange("half typed")
        f.selected().resetCalls()
        vm.onCancelEditing()
        assertFalse(f.state().isEditingUrl)
        assertTrue(f.state().suggestions.isEmpty())
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertEquals("google.com", f.state().displayHost)
    }

    @Test
    fun startEditing_entersEditMode_withoutLoading() {
        f.selected().resetCalls()
        vm.onStartEditing()
        assertTrue(f.state().isEditingUrl)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
    }

    // --- AC15 / AC16 ---

    @Test
    fun historyFlags_followSession() {
        f = BrowserFixture.onPage("https://start.example/")
        val s = f.selected()
        s.visit("https://a.com/")
        s.visit("https://b.com/")
        assertTrue(f.state().canGoBack)
        assertFalse(f.state().canGoForward)
        vm.onBack()
        s.fake.simulatePageFinished("https://a.com/")
        assertTrue(f.state().canGoForward)
    }

    @Test
    fun back_forward_home_delegateToSelectedSession() {
        f = BrowserFixture.onPage("https://start.example/")
        val s = f.selected()
        s.visit("https://a.com/")
        s.visit("https://b.com/")
        s.resetCalls()
        vm.onBack()
        assertEquals(1, s.goBackCalls)
        s.fake.simulatePageFinished("https://a.com/")
        vm.onForward()
        assertEquals(1, s.goForwardCalls)
        vm.onHome()
        // Spec 010: Home hiển thị trang chủ native, không tải URL nào.
        assertTrue(f.state().isHome)
        assertTrue(s.loadUrlCalls.isEmpty())
    }

    @Test
    fun back_whenCannotGoBack_doesNotCallGoBack() {
        val s = f.selected()
        s.resetCalls()
        vm.onBack()
        vm.onForward()
        assertEquals(0, s.goBackCalls)
        assertEquals(0, s.goForwardCalls)
    }

    // --- AC17 ---

    @Test
    fun tabCount_followsTabManagerImmediately() {
        f.tabManager.openTab("https://a.com")
        assertEquals(2, f.state().tabCount)
        f.tabManager.openTab("https://b.com", select = false)
        assertEquals(3, f.state().tabCount)
    }

    // --- AC18 / AC19 ---

    @Test
    fun scrollToTopAndBottom_reachSelectedSession() {
        vm.onScrollToTop()
        vm.onScrollToBottom()
        assertEquals(listOf(ScrollCommand.Top, ScrollCommand.Bottom), f.selected().fake.scrollCommands)
    }

    // --- AC21 / AC25 / AC30 ---

    @Test
    fun newTab_appendsSelectedHomeTab_withoutLoad() {
        val before =
            f.tabManager.tabs.value
                .map { it.id }
        vm.onNewTab()
        val after = f.tabManager.tabs.value
        assertEquals(before.size + 1, after.size)
        assertEquals(before, after.dropLast(1).map { it.id })
        val newId = after.last().id
        assertEquals(newId, f.tabManager.selectedTabId.value)
        assertTrue(
            f.tabManager
                .spy(newId)
                .loadUrlCalls
                .isEmpty(),
        )
        assertTrue(f.state().isHome)
        assertEquals(2, f.state().tabCount)
        assertEquals(newId, f.state().selectedTabId)
    }

    // --- AC22 / AC24 ---

    @Test
    fun reload_reloadsSelectedSession() {
        f = BrowserFixture.onPage("https://start.example/")
        val s = f.selected()
        vm.onReload()
        assertEquals(1, s.reloadCalls)
        assertTrue(s.fake.state.value.isLoading)
        assertTrue(f.state().isLoading)
    }

    // --- AC27 / AC28 / AC29 ---

    @Test
    fun tabs_listedInTabManagerOrder_withTitles() {
        val a = f.tabManager.openTab("https://a.com")
        f.tabManager
            .spy(a.id)
            .fake
            .simulateTitle("Alpha")
        val b = f.tabManager.openTab("https://b.com")
        assertEquals(
            f.tabManager.tabs.value
                .map { it.id },
            f.state().tabs.map { it.id },
        )
        assertEquals(
            "Alpha",
            f
                .state()
                .tabs
                .first { it.id == a.id }
                .title,
        )
        assertEquals(
            "https://b.com",
            f
                .state()
                .tabs
                .first { it.id == b.id }
                .url,
        )
    }

    @Test
    fun selectTab_selectsInTabManager() {
        val first = f.tabManager.selectedTabId.value!!
        f.tabManager.openTab("https://a.com")
        vm.onSelectTab(first)
        assertEquals(first, f.tabManager.selectedTabId.value)
        assertEquals(first, f.state().selectedTabId)
        assertTrue(f.state().isHome)
        assertEquals("", f.state().url)
    }

    @Test
    fun closeTab_removesIt_andClosingLastCreatesHomeTab() {
        val first = f.tabManager.selectedTabId.value!!
        val a = f.tabManager.openTab("https://a.com")
        vm.onCloseTab(a.id)
        assertEquals(listOf(first), f.state().tabs.map { it.id })
        assertEquals(1, f.state().tabCount)
        vm.onCloseTab(first)
        assertEquals(1, f.state().tabs.size)
        assertEquals(1, f.state().tabCount)
        assertEquals(f.tabManager.selectedTabId.value, f.state().selectedTabId)
    }
}
