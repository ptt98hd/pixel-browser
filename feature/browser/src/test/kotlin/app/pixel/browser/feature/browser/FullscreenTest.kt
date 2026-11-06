package app.pixel.browser.feature.browser

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_BACK
import app.pixel.browser.feature.browser.BrowserTestTags.FULLSCREEN_CONTAINER
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 007 AC20 – UI fullscreen qua [BrowserRoute] + ViewModel thật (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class FullscreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    /** Tab có lịch sử (canGoBack = true) để kiểm Back ưu tiên fullscreen hơn goBack (AC31c). */
    private fun launchFullscreen() {
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
        f.selected().visit("https://a.com/video")
        f.selected().resetCalls()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulateEnterFullscreen() }
        rule.waitForIdle()
    }

    private fun keepScreenOn(): Boolean =
        rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun pressBack() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun ac20_fullscreen_showsContainer_hidesChrome_andKeepsScreenOn() {
        launchFullscreen()
        rule.onNodeWithTag(FULLSCREEN_CONTAINER).assertExists()
        rule.onNodeWithTag(URL_DISPLAY).assertDoesNotExist()
        rule.onNodeWithTag(BTN_BACK).assertDoesNotExist()
        assertTrue(keepScreenOn())
    }

    @Test
    fun ac20_back_exitsFullscreen_beforeGoBack() {
        launchFullscreen()
        pressBack()
        assertEquals(1, f.selected().fake.exitFullscreenCalls)
        assertEquals(0, f.selected().goBackCalls)
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun ac20_leavingFullscreen_restoresChrome_andClearsKeepScreenOn() {
        launchFullscreen()
        rule.runOnIdle { f.selected().fake.simulateExitFullscreen() }
        rule.waitForIdle()
        rule.onNodeWithTag(FULLSCREEN_CONTAINER).assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertExists()
        rule.onNodeWithTag(BTN_BACK).assertExists()
        assertFalse(keepScreenOn())
    }
}
