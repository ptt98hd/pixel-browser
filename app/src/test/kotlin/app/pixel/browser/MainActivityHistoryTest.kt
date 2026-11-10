package app.pixel.browser

import android.content.MutableContextWrapper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.engine.FakeProfileGateway
import app.pixel.browser.core.engine.PrivateSpace
import app.pixel.browser.core.engine.RetryScheduler
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.history.HistoryTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

/** Spec 012 AC17 (phần `:app`), AC19, AC20 – điều hướng lớp phủ Lịch sử và ghi end-to-end. */
@RunWith(RobolectricTestRunner::class)
class MainActivityHistoryTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val store = InMemoryHistoryStore()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val page = "https://example.org/page"

    @Before
    fun bindStore() {
        (RuntimeEnvironment.getApplication() as PixelBrowserApplication)
            .bindHistoryStore(store, CoroutineScope(Dispatchers.Unconfined))
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

    private fun tabManager(vm: BrowserViewModel): TabManager =
        BrowserViewModel::class.java
            .getDeclaredField("tabManager")
            .apply { isAccessible = true }
            .get(vm) as TabManager

    private fun onMain(
        s: ActivityScenario<MainActivity>,
        block: () -> Unit,
    ) {
        s.onActivity { block() }
        rule.waitForIdle()
    }

    private fun openUrl(
        s: ActivityScenario<MainActivity>,
        url: String,
    ): WebViewEngineSession {
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl(url) }
        return vm.selectedSession.value as WebViewEngineSession
    }

    private fun openHistory() {
        rule.onNodeWithTag(BrowserTestTags.BTN_MENU).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.MENU_HISTORY).performClick()
        rule.waitForIdle()
    }

    private val historyItem =
        SemanticsMatcher("history item") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("history_item_")
        }

    private fun itemCount() = rule.onAllNodes(historyItem).fetchSemanticsNodes().size

    private fun resetPauseFlags(webView: android.webkit.WebView) {
        val shadow: ShadowWebView = shadowOf(webView)
        listOf("onPauseCalled", "onResumeCalled").forEach {
            ShadowWebView::class.java
                .getDeclaredField(it)
                .apply { isAccessible = true }
                .setBoolean(shadow, false)
        }
    }

    private fun urlText(): String =
        rule
            .onNodeWithTag(BrowserTestTags.URL_DISPLAY)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.Text) { emptyList() }
            .joinToString(" ") { it.text }

    // --- AC19 ---

    @Test
    fun ac19_menuHistory_atHome_opensFullScreenHistory() {
        launch()
        openHistory()
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertExists()
        val root = rule.onRoot().getBoundsInRoot()
        val screen = rule.onNodeWithTag(HistoryTestTags.SCREEN).getBoundsInRoot()
        assertEquals(root.width, screen.width)
        assertEquals(root.height, screen.height)
    }

    @Test
    fun ac19_menuHistory_isEnabledAtHome() {
        launch()
        rule.onNodeWithTag(BrowserTestTags.BTN_MENU).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.MENU_HISTORY).assertIsEnabled()
    }

    @Test
    fun ac19_open_pausesSelectedSession_close_resumesIt() {
        val s = launch()
        val session = openUrl(s, page)
        resetPauseFlags(session.webView)
        openHistory()
        assertTrue("page must be paused", shadowOf(session.webView).wasOnPauseCalled())
        resetPauseFlags(session.webView)
        rule.onNodeWithTag(HistoryTestTags.CLOSE).performClick()
        rule.waitForIdle()
        assertTrue("page must resume", shadowOf(session.webView).wasOnResumeCalled())
    }

    private fun isHiddenFromAccessibility(tag: String): Boolean {
        var node: androidx.compose.ui.semantics.SemanticsNode? =
            rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        while (node != null) {
            if (SemanticsProperties.HideFromAccessibility in node.config) return true
            node = node.parent
        }
        return false
    }

    @Test
    fun ac19_browserContent_isHiddenFromAccessibility_onlyWhileOverlayIsOpen() {
        launch()
        assertTrue(!isHiddenFromAccessibility(BrowserTestTags.BTN_MENU))
        openHistory()
        assertTrue(isHiddenFromAccessibility(BrowserTestTags.BTN_MENU))
        rule.onNodeWithTag(HistoryTestTags.CLOSE).performClick()
        rule.waitForIdle()
        assertTrue(!isHiddenFromAccessibility(BrowserTestTags.BTN_MENU))
    }

    @Test
    fun ac19_closeButton_closesOverlay_leavingTabAndUrlUnchanged() {
        val s = launch()
        val vm = vm(s)
        openUrl(s, page)
        val before = vm.uiState.value
        openHistory()
        rule.onNodeWithTag(HistoryTestTags.CLOSE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertDoesNotExist()
        assertEquals(before.url, vm.uiState.value.url)
        assertEquals(before.tabCount, vm.uiState.value.tabCount)
        assertEquals(before.selectedTabId, vm.uiState.value.selectedTabId)
    }

    @Test
    fun ac19_systemBack_closesOverlay_beforeReachingBrowser() {
        val s = launch()
        val vm = vm(s)
        openUrl(s, page)
        openHistory()
        s.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertDoesNotExist()
        assertEquals(page, vm.uiState.value.url)
    }

    @Test
    fun ac19_rotation_keepsOverlayOpen() {
        val s = launch()
        openHistory()
        s.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertExists()
    }

    // --- AC17 (qua :app) ---

    @Test
    fun ac17_tapItem_loadsUrlInCurrentTab_closesOverlay_showsHost() {
        val s = launch()
        val vm = vm(s)
        runBlocking { store.insert("https://opened.example/x", "Opened", System.currentTimeMillis()) }
        openHistory()
        rule.onAllNodes(historyItem)[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertDoesNotExist()
        assertEquals(1, vm.uiState.value.tabCount)
        assertEquals("https://opened.example/x", vm.uiState.value.url)
        assertEquals("opened.example", urlText())
    }

    @Test
    fun ac17_menuOpenInNewTab_selectsNewTab_closesOverlay() {
        val s = launch()
        val vm = vm(s)
        runBlocking { store.insert("https://opened.example/x", "Opened", System.currentTimeMillis()) }
        openHistory()
        rule.onAllNodes(historyItem)[0].performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.MENU_OPEN_NEW_TAB).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertDoesNotExist()
        assertEquals(2, vm.uiState.value.tabCount)
        assertEquals("https://opened.example/x", vm.uiState.value.url)
    }

    // --- AC20 ---

    @Test
    fun ac20_normalTab_finishedPage_appearsInHistory() {
        val s = launch()
        val session = openUrl(s, page)
        onMain(s) { session.dispatchPageFinished(page) }
        openHistory()
        assertEquals(1, itemCount())
    }

    @Test
    fun ac20_privateTab_finishedPage_isNotInHistory() {
        // Spec 016: tab ẩn danh cần PrivateSpace khả dụng (fail-closed); dùng gateway giả với WebView thật.
        val app = RuntimeEnvironment.getApplication() as PixelBrowserApplication
        val gateway = FakeProfileGateway()
        app.bindPrivateSpace(PrivateSpace(gateway, scheduler = RetryScheduler { _, _ -> }))
        app.bindBaseSessionFactory { profile ->
            WebViewEngineSession(MutableContextWrapper(app), profile, gateway)
        }
        val s = launch()
        val vm = vm(s)
        val manager = tabManager(vm)
        lateinit var privateId: String
        onMain(s) { privateId = manager.openTab(page, select = true, isPrivate = true).id }
        val session = manager.session(privateId) as WebViewEngineSession
        onMain(s) { session.dispatchPageFinished(page) }
        openHistory()
        assertEquals(0, itemCount())
        rule.onNodeWithTag(HistoryTestTags.EMPTY).assertExists()
    }

    @Test
    fun ac20_errorPage_isNotInHistory() {
        val s = launch()
        val session = openUrl(s, page)
        onMain(s) {
            session.dispatchMainFrameError()
            session.dispatchPageFinished(page)
        }
        openHistory()
        assertEquals(0, itemCount())
    }
}
