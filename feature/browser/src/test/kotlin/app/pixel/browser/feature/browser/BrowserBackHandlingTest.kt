package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_RELOAD
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SHEET
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import app.pixel.browser.feature.browser.BrowserTestTags.URL_FIELD
import app.pixel.browser.feature.browser.intent.IncomingRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Spec 005 – Back hệ thống: AC13, AC31 (a)–(d). */
@RunWith(RobolectricTestRunner::class)
class BrowserBackHandlingTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    /** Tab được chọn có lịch sử a → b (canGoBack = true) nếu [withHistory]. */
    private fun launch(withHistory: Boolean) {
        f = BrowserFixture()
        if (withHistory) {
            f.selected().visit("https://a.com/")
            f.selected().visit("https://www.b.com/")
        }
        f.selected().resetCalls()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    // --- AC31 (a) ---

    @Test
    fun ac31a_back_closesTabSheet_withoutGoBack() {
        launch(withHistory = true)
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.onNodeWithTag(TAB_SHEET).assertExists()
        pressBack()
        rule.onNodeWithTag(TAB_SHEET).assertDoesNotExist()
        assertEquals(0, f.selected().goBackCalls)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun ac31a_back_closesMenu_withoutGoBack() {
        launch(withHistory = true)
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_RELOAD).assertExists()
        pressBack()
        rule.onNodeWithTag(MENU_RELOAD).assertDoesNotExist()
        assertEquals(0, f.selected().goBackCalls)
        assertFalse(rule.activity.isFinishing)
    }

    // --- AC13 / AC31 (b) ---

    @Test
    fun ac13_ac31b_back_cancelsEditing_restoresDomain_withoutLoadOrGoBack() {
        launch(withHistory = true)
        rule.onNodeWithTag(URL_DISPLAY).performClick()
        rule.onNodeWithTag(URL_FIELD).performTextReplacement("half typ")
        pressBack()
        rule.onNodeWithTag(URL_FIELD).assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("b.com")
        assertTrue(f.selected().loadUrlCalls.isEmpty())
        assertEquals(0, f.selected().goBackCalls)
        assertFalse(f.viewModel.uiState.value.isEditingUrl)
        assertFalse(rule.activity.isFinishing)
    }

    // --- AC31 (c) ---

    @Test
    fun ac31c_back_withHistory_goesBack_andActivityStays() {
        launch(withHistory = true)
        pressBack()
        assertEquals(1, f.selected().goBackCalls)
        assertFalse(rule.activity.isFinishing)
    }

    // --- AC31 (d) ---

    @Test
    fun ac31d_back_withoutHistory_fallsThroughToSystem_withoutGoBack() {
        launch(withHistory = false)
        rule.onNodeWithTag(URL_DISPLAY).assertExists()
        rule.runOnIdle {
            assertFalse(
                "browser must not intercept Back when nothing to handle",
                rule.activity.onBackPressedDispatcher.hasEnabledCallbacks(),
            )
        }
        pressBack()
        assertEquals(0, f.selected().goBackCalls)
    }

    // --- Spec 006 AC14: Back trên tab mở từ app ngoài ---

    /** Tab đang chọn có lịch sử (không phải tab home chưa dùng) để intent mở tab mới. */
    private fun launchWithUsedTab() {
        f = BrowserFixture()
        f.selected().fake.simulatePageFinished(f.homeUrl)
        f.selected().visit("https://x.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    /** Gửi intent VIEW [url] vào ViewModel; trả id tab nhận URL, đã tải xong (canGoBack = false). */
    private fun openFromIntent(url: String): String {
        var id: String? = null
        rule.runOnIdle {
            f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl(url))
            id = f.tabManager.selectedTabId.value
            f.tabManager
                .spy(id!!)
                .fake
                .simulatePageFinished(url)
            f.tabManager.spy(id!!).resetCalls()
        }
        rule.waitForIdle()
        return id!!
    }

    private fun tabIds(): List<String> =
        f.tabManager.tabs.value
            .map { it.id }

    @Test
    fun ac14_back_onIntentTabWithoutHistory_closesTab_andMovesTaskToBack() {
        launchWithUsedTab()
        val id = openFromIntent("https://b.com/")
        assertEquals(2, tabIds().size)
        assertFalse(
            f.tabManager
                .spy(id)
                .state.value.canGoBack,
        )

        pressBack()

        assertFalse(id in tabIds())
        assertEquals(1, tabIds().size)
        assertEquals(0, f.factory.created.sumOf { it.goBackCalls })
        assertTrue(shadowOf(rule.activity).isTaskMovedToBack)
    }

    @Test
    fun ac14_back_onReusedHomeTabFromIntent_closesTab_andMovesTaskToBack() {
        launch(withHistory = false)
        val homeId = f.tabManager.selectedTabId.value
        val id = openFromIntent("https://b.com/")
        assertEquals(homeId, id)

        pressBack()

        assertFalse(id in tabIds())
        assertTrue(shadowOf(rule.activity).isTaskMovedToBack)
    }

    @Test
    fun ac14_back_onIntentTabWithHistory_goesBack_andStays() {
        launchWithUsedTab()
        val id = openFromIntent("https://b.com/")
        rule.runOnIdle {
            f.tabManager.spy(id).visit("https://b.com/next")
            f.tabManager.spy(id).resetCalls()
        }
        rule.waitForIdle()

        pressBack()

        assertEquals(1, f.tabManager.spy(id).goBackCalls)
        assertTrue(id in tabIds())
        assertFalse(shadowOf(rule.activity).isTaskMovedToBack)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun ac14_back_onNormalTabWithoutHistory_keepsAc31d() {
        launch(withHistory = false)
        val before = tabIds()
        pressBack()
        assertEquals(before, tabIds())
        assertFalse(shadowOf(rule.activity).isTaskMovedToBack)
    }
}
