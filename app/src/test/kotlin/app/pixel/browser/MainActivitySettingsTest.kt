package app.pixel.browser

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.data.search.FakeSearchSettingsStore
import app.pixel.browser.core.data.search.SearchEngineIds
import app.pixel.browser.core.data.search.SearchSettings
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.home.HomeTestTags
import app.pixel.browser.feature.settings.SettingsTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowWebView

/** Spec 014 AC18 (menu → Cài đặt) và AC19 (end-to-end chọn engine). */
@RunWith(RobolectricTestRunner::class)
class MainActivitySettingsTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication
    private val store = FakeSearchSettingsStore()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val page = "https://example.org/page"

    @Before
    fun bindStore() {
        app.bindSearchStore(store, CoroutineScope(Dispatchers.Unconfined))
        // Giữ gợi ý ở bộ nhớ: provider Room chạy tuần tự ở thread khác nên waitForIdle() có thể xong trước (flaky).
        app.bindBookmarkRepository(InMemoryBookmarkRepository())
        app.bindHistoryStore(InMemoryHistoryStore(), CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            scenario = it
            rule.waitForIdle()
        }

    private fun vm(s: ActivityScenario<MainActivity>): BrowserViewModel {
        lateinit var vm: BrowserViewModel
        s.onActivity { vm = ViewModelProvider(it)[BrowserViewModel::class.java] }
        return vm
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openSettings() {
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_SETTINGS)
    }

    private fun systemBack(s: ActivityScenario<MainActivity>) {
        s.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun resetPauseFlags(webView: android.webkit.WebView) {
        val shadow: ShadowWebView = shadowOf(webView)
        listOf("onPauseCalled", "onResumeCalled").forEach {
            ShadowWebView::class.java
                .getDeclaredField(it)
                .apply { isAccessible = true }
                .setBoolean(shadow, false)
        }
    }

    private fun openUrl(
        s: ActivityScenario<MainActivity>,
        url: String,
    ): WebViewEngineSession {
        val vm = vm(s)
        s.onActivity { vm.onOpenUrl(url) }
        rule.waitForIdle()
        return vm.selectedSession.value as WebViewEngineSession
    }

    private fun chooseEngineInSettings(optionId: String) {
        openSettings()
        tap(SettingsTestTags.item("search_engine"))
        tap(SettingsTestTags.searchEngineOption(optionId))
    }

    private fun lastLoaded(session: WebViewEngineSession): String? = shadowOf(session.webView).lastLoadedUrl

    // --- AC18 ---

    @Test
    fun ac18_menuSettings_atHome_opensFullScreenSettings() {
        launch()
        openSettings()
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertExists()
        val root = rule.onRoot().getBoundsInRoot()
        val screen = rule.onNodeWithTag(SettingsTestTags.SCREEN).getBoundsInRoot()
        assertEquals(root.width, screen.width)
        assertEquals(root.height, screen.height)
    }

    @Test
    fun ac18_menuSettings_isEnabledAtHome() {
        launch()
        tap(BrowserTestTags.BTN_MENU)
        rule.onNodeWithTag(BrowserTestTags.MENU_SETTINGS).assertIsEnabled()
    }

    @Test
    fun ac18_open_pausesSelectedSession_back_resumesIt() {
        val s = launch()
        val session = openUrl(s, page)
        resetPauseFlags(session.webView)
        openSettings()
        assertTrue("page must be paused", shadowOf(session.webView).wasOnPauseCalled())
        resetPauseFlags(session.webView)
        tap(SettingsTestTags.BACK)
        assertTrue("page must resume", shadowOf(session.webView).wasOnResumeCalled())
    }

    @Test
    fun ac18_backButtonAndSystemBack_closeSettings_leavingTabAndUrlUnchanged() {
        val s = launch()
        val vm = vm(s)
        openUrl(s, page)
        val before = vm.uiState.value

        openSettings()
        tap(SettingsTestTags.BACK)
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertDoesNotExist()

        openSettings()
        systemBack(s)
        rule.onNodeWithTag(SettingsTestTags.SCREEN).assertDoesNotExist()

        assertEquals(before.url, vm.uiState.value.url)
        assertEquals(before.tabCount, vm.uiState.value.tabCount)
        assertEquals(before.selectedTabId, vm.uiState.value.selectedTabId)
        rule.onNodeWithTag(BrowserTestTags.MENU_SETTINGS).assertDoesNotExist()
    }

    @Test
    fun ac18_rotationOnSubPage_staysOnThatPage() {
        val s = launch()
        openSettings()
        tap(SettingsTestTags.item("search_engine"))
        rule.onNodeWithTag(SettingsTestTags.page("search_engine")).assertExists()
        s.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTestTags.page("search_engine")).assertExists()
    }

    @Test
    fun ac18_themeAppearancePage_isReachable() {
        launch()
        openSettings()
        tap(SettingsTestTags.item("theme"))
        rule.onNodeWithTag(SettingsTestTags.appearanceMode("dark")).assertExists()
    }

    // --- AC19 ---

    @Test
    fun ac19_chooseDuckDuckGo_storeAndAppEngineUpdate() {
        launch()
        chooseEngineInSettings("duckduckgo")
        assertEquals("duckduckgo", app.searchSettings.value.selectedId)
        assertEquals("duckduckgo", app.searchEngine.value.id)
    }

    @Test
    fun ac19_afterChoosingDuckDuckGo_homeSubmitLoadsDuckDuckGoSearchUrl() {
        val s = launch()
        chooseEngineInSettings("duckduckgo")
        systemBack(s) // trang con → danh sách
        systemBack(s) // danh sách → trình duyệt

        tap(HomeTestTags.HOME_SEARCH)
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performTextInput("kotlin")
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performImeAction()
        rule.waitForIdle()

        val session = vm(s).selectedSession.value as WebViewEngineSession
        assertEquals("https://duckduckgo.com/?q=kotlin", lastLoaded(session))
    }

    @Test
    fun ac19_afterChoosingDuckDuckGo_homePlaceholderMentionsIt() {
        val s = launch()
        chooseEngineInSettings("duckduckgo")
        systemBack(s)
        systemBack(s)
        val text =
            rule
                .onNodeWithTag(HomeTestTags.HOME_SEARCH)
                .fetchSemanticsNode()
                .config
                .getOrElse(SemanticsProperties.Text) { emptyList() }
                .joinToString(" ") { it.text }
        assertTrue(text, "DuckDuckGo" in text)
    }

    @Test
    fun ac19_afterChoosingDuckDuckGo_firstSuggestionIsSearchWithDuckDuckGoUrl() {
        val s = launch()
        chooseEngineInSettings("duckduckgo")
        systemBack(s)
        systemBack(s)

        tap(HomeTestTags.HOME_SEARCH)
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performTextInput("kotlin")
        rule.waitForIdle()

        rule.onNodeWithTag(BrowserTestTags.suggestion(0)).assertExists()
        val first =
            vm(s)
                .uiState.value.suggestions
                .first()
        assertEquals(SuggestionKind.Search, first.kind)
        assertEquals("https://duckduckgo.com/?q=kotlin", first.url)
    }

    @Test
    fun ac19_customEngine_savedInSettings_isUsedBySubmit() {
        val s = launch()
        openSettings()
        tap(SettingsTestTags.item("search_engine"))
        tap(SettingsTestTags.searchEngineOption("custom"))
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).performTextInput("Kagi")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).performTextInput("kagi.com/search?q=%s")
        tap(SettingsTestTags.CUSTOM_SAVE)
        systemBack(s)
        systemBack(s)

        tap(HomeTestTags.HOME_SEARCH)
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performTextInput("a b")
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performImeAction()
        rule.waitForIdle()

        assertEquals(SearchEngineIds.CUSTOM, app.searchSettings.value.selectedId)
        val session = vm(s).selectedSession.value as WebViewEngineSession
        assertEquals("https://kagi.com/search?q=a%20b", lastLoaded(session))
    }

    @Test
    fun ac19_activityRestart_withStoredDuckDuckGo_stillUsesIt() {
        // Store giữ duckduckgo → sau khi nạp, Activity mới dùng DuckDuckGo.
        val persisted = FakeSearchSettingsStore(SearchSettings(selectedId = "duckduckgo"))
        app.bindSearchStore(persisted, CoroutineScope(Dispatchers.Unconfined))
        val s = launch()
        s.recreate()
        rule.waitForIdle()

        tap(HomeTestTags.HOME_SEARCH)
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performTextInput("kotlin")
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performImeAction()
        rule.waitForIdle()

        val session = vm(s).selectedSession.value as WebViewEngineSession
        assertEquals("https://duckduckgo.com/?q=kotlin", lastLoaded(session))
    }
}
