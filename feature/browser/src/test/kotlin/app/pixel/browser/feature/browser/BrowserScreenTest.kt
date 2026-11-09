package app.pixel.browser.feature.browser

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.pixel.browser.core.engine.ScrollCommand
import app.pixel.browser.core.omnibox.searchUrl
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_BACK
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_FORWARD
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_HOME
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_CONTAINER
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_PLACEHOLDER
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_NEW_TAB
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_RELOAD
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_SHARE
import app.pixel.browser.feature.browser.BrowserTestTags.PROGRESS
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_COUNT
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import app.pixel.browser.feature.browser.BrowserTestTags.URL_FIELD
import app.pixel.browser.feature.browser.BrowserTestTags.suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 005 – màn hình đầy đủ (BrowserRoute + ViewModel + TabManager thật + Fake). */
@RunWith(RobolectricTestRunner::class)
class BrowserScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    /** [page] khác null → bắt đầu ở một tab trang web thay vì tab home (spec 010). */
    private fun launch(
        page: String? = null,
        setup: BrowserFixture.() -> Unit = {},
    ) {
        f = if (page != null) BrowserFixture.onPage(page) else BrowserFixture()
        f.setup()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun onUi(block: () -> Unit) = rule.runOnIdle(block)

    private companion object {
        const val START = "https://start.example/"
    }

    // --- AC2 ---

    @Test
    fun ac2_fakeSessionWithoutView_showsPlaceholder() {
        launch(page = START)
        rule.onNodeWithTag(ENGINE_PLACEHOLDER, useUnmergedTree = true).assertExists()
    }

    @Test
    fun ac2_engineContainer_withNullView_showsPlaceholder() {
        rule.setContent { EngineContainer(view = null) }
        rule.onNodeWithTag(ENGINE_PLACEHOLDER, useUnmergedTree = true).assertExists()
    }

    @Test
    fun ac2_engineContainer_withView_attachesItInsideContainer() {
        val webView = WebView(rule.activity)
        rule.setContent { EngineContainer(view = webView) }
        rule.waitForIdle()
        rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(ENGINE_PLACEHOLDER, useUnmergedTree = true).assertDoesNotExist()
        assertNotNull(webView.parent)
        assertTrue(webView.isAttachedToWindow)
    }

    // --- AC3 ---

    @Test
    fun ac3_switchingTab_updatesUrlAndBackButton() {
        launch {
            val a = tabManager.openTab("https://a.com/start")
            tabManager.spy(a.id).visit("https://a.com/start")
            tabManager.spy(a.id).visit("https://a.com")
            val b = tabManager.openTab("https://b.com")
            tabManager.spy(b.id).fake.simulatePageFinished("https://b.com")
            tabManager.selectTab(a.id)
        }
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("a.com")
        rule.onNodeWithTag(BTN_BACK).assertIsEnabled()
        val a = f.tabManager.selectedTabId.value!!
        val b =
            f.tabManager.tabs.value
                .last()
                .id

        onUi { f.viewModel.onSelectTab(b) }

        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("b.com")
        rule.onNodeWithTag(BTN_BACK).assertIsNotEnabled()
        assertFalse(
            f.tabManager
                .spy(a)
                .fake.isClosed,
        )
    }

    // --- AC7 ---

    @Test
    fun ac7_progressFromSession_isShownLive() {
        launch(page = START)
        onUi {
            f.selected().reload()
            f.selected().fake.simulateProgress(70)
        }
        assertEquals(0.7f, rule.onNodeWithTag(PROGRESS, useUnmergedTree = true).progressFraction(), 0.001f)
        onUi { f.selected().fake.simulatePageFinished(START) }
        rule.onNodeWithTag(PROGRESS, useUnmergedTree = true).assertDoesNotExist()
    }

    // --- AC10 ---

    @Test
    fun ac10_typeUrlAndGo_loadsOnce_andExitsEditing() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(URL_DISPLAY).performClick()
        rule.onNodeWithTag(URL_FIELD).performTextReplacement("example.com")
        rule.onNodeWithTag(URL_FIELD).performImeAction()
        rule.waitForIdle()
        assertEquals(listOf("https://example.com"), f.selected().loadUrlCalls)
        assertEquals(
            "https://example.com",
            f
                .selected()
                .fake.state.value.url,
        )
        rule.onNodeWithTag(URL_FIELD).assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("example.com")
    }

    @Test
    fun ac10_typeTextAndGo_loadsSearchUrl() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(URL_DISPLAY).performClick()
        rule.onNodeWithTag(URL_FIELD).performTextReplacement("hello world")
        rule.onNodeWithTag(URL_FIELD).performImeAction()
        rule.waitForIdle()
        assertEquals(listOf(f.viewModel.searchEngine.searchUrl("hello world")), f.selected().loadUrlCalls)
    }

    @Test
    fun ac11_blockedInput_staysEditing_withoutLoading() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(URL_DISPLAY).performClick()
        rule.onNodeWithTag(URL_FIELD).performTextReplacement("javascript:alert(1)")
        rule.onNodeWithTag(URL_FIELD).performImeAction()
        rule.waitForIdle()
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        rule.onNodeWithTag(URL_FIELD).assertExists()
    }

    // --- AC12 ---

    @Test
    fun ac12_typing_showsSuggestions_andTapSearchSuggestionLoadsIt() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(URL_DISPLAY).performClick()
        rule.onNodeWithTag(URL_FIELD).performTextReplacement("example.com")
        rule.waitForIdle()
        rule.onNodeWithTag(suggestion(0)).assertExists()
        rule.onNodeWithTag(suggestion(1)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(f.viewModel.searchEngine.searchUrl("example.com")), f.selected().loadUrlCalls)
        rule.onNodeWithTag(URL_FIELD).assertDoesNotExist()
    }

    // --- AC15 ---

    @Test
    fun ac15_buttonsFollowFakeHistory() {
        launch(page = START)
        rule.onNodeWithTag(BTN_BACK).assertIsNotEnabled()
        onUi {
            f.selected().visit("https://a.com/")
            f.selected().visit("https://b.com/")
        }
        rule.onNodeWithTag(BTN_BACK).assertIsEnabled()
        rule.onNodeWithTag(BTN_FORWARD).assertIsNotEnabled()
        onUi {
            f.selected().goBack()
            f.selected().fake.simulatePageFinished("https://a.com/")
        }
        rule.onNodeWithTag(BTN_FORWARD).assertIsEnabled()
    }

    // --- AC16 ---

    @Test
    fun ac16_tapBackAndForward_navigateSelectedSession() {
        launch(page = START) {
            selected().visit("https://a.com/")
            selected().visit("https://b.com/")
        }
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_BACK).performClick()
        assertEquals(1, f.selected().goBackCalls)
        onUi { f.selected().fake.simulatePageFinished("https://a.com/") }
        rule.onNodeWithTag(BTN_FORWARD).performClick()
        assertEquals(1, f.selected().goForwardCalls)
    }

    @Test
    fun ac16_tapDisabledBack_doesNotCallGoBack() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_BACK).performClick()
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac16_tapHome_showsHomeInCurrentTab_withoutLoad() {
        launch(page = START) { selected().visit("https://a.com/") }
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_HOME).performClick()
        rule.waitForIdle()
        // Spec 010: home là trạng thái của tab, không loadUrl.
        assertTrue(f.viewModel.uiState.value.isHome)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertEquals(1, f.tabManager.tabs.value.size)
    }

    @Test
    fun ac16_tapTabs_opensTabSwitcher() {
        launch()
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
    }

    @Test
    fun ac16_tapMenu_showsMenuItems() {
        launch()
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_RELOAD).assertExists()
        rule.onNodeWithTag(MENU_NEW_TAB).assertExists()
        rule.onNodeWithTag(MENU_SHARE).assertExists()
    }

    // --- AC17 ---

    @Test
    fun ac17_tabCount_increasesWhenTabOpened() {
        launch()
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("1")
        onUi { f.tabManager.openTab("https://a.com") }
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("2")
    }

    // --- AC18 / AC19 ---

    @Test
    fun ac18_longPressBack_scrollsToTop_withoutGoBack() {
        launch(page = START) {
            selected().visit("https://a.com/")
            selected().visit("https://b.com/")
        }
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_BACK).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf(ScrollCommand.Top), f.selected().fake.scrollCommands)
        assertEquals(0, f.selected().goBackCalls)
    }

    @Test
    fun ac18_longPressBack_worksWhenCannotGoBack() {
        launch()
        rule.onNodeWithTag(BTN_BACK).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf(ScrollCommand.Top), f.selected().fake.scrollCommands)
    }

    @Test
    fun ac19_longPressForward_scrollsToBottom_evenWhenCannotGoForward() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_FORWARD).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(listOf(ScrollCommand.Bottom), f.selected().fake.scrollCommands)
        assertEquals(0, f.selected().goForwardCalls)
    }

    // --- AC20 ---

    @Test
    fun ac20_longPressHome_focusesUrlField_withoutLoading() {
        launch()
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_HOME).performTouchInput { longClick() }
        rule.onNodeWithTag(URL_FIELD).assertIsFocused()
        assertTrue(f.selected().loadUrlCalls.isEmpty())
    }

    // --- AC21 ---

    @Test
    fun ac21_longPressTabs_opensNewSelectedHomeTab_withoutSwitcher() {
        launch()
        val before =
            f.tabManager.tabs.value
                .map { it.id }
        rule.onNodeWithTag(BTN_TABS).performTouchInput { longClick() }
        rule.waitForIdle()
        val after = f.tabManager.tabs.value
        assertEquals(before, after.dropLast(1).map { it.id })
        assertEquals(after.last().id, f.tabManager.selectedTabId.value)
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertTrue(f.viewModel.uiState.value.isHome)
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("2")
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
    }

    // --- AC22 ---

    @Test
    fun ac22_longPressMenu_reloads_withoutMenu() {
        launch(page = START)
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_MENU).performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(1, f.selected().reloadCalls)
        assertTrue(
            f
                .selected()
                .fake.state.value.isLoading,
        )
        rule.onNodeWithTag(MENU_RELOAD).assertDoesNotExist()
    }

    // --- AC24 / AC25 ---

    @Test
    fun ac24_menuReload_reloads_andClosesMenu() {
        launch(page = START)
        f.selected().resetCalls()
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_RELOAD).performClick()
        rule.waitForIdle()
        assertEquals(1, f.selected().reloadCalls)
        rule.onNodeWithTag(MENU_RELOAD).assertDoesNotExist()
    }

    @Test
    fun ac25_menuNewTab_opensSelectedHomeTab_andClosesMenu() {
        launch()
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_NEW_TAB).performClick()
        rule.waitForIdle()
        assertEquals(2, f.tabManager.tabs.value.size)
        assertEquals(
            f.tabManager.tabs.value
                .last()
                .id,
            f.tabManager.selectedTabId.value,
        )
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertTrue(f.viewModel.uiState.value.isHome)
        rule.onNodeWithTag(MENU_NEW_TAB).assertDoesNotExist()
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("2")
    }
}
