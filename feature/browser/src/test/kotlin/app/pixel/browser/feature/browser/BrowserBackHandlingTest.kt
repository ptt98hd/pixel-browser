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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
