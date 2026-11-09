package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 012 AC19 – `BrowserRoute(overlayOpen)`: trang bị pause khi màn hình khác phủ lên. */
@RunWith(RobolectricTestRunner::class)
class BrowserRouteOverlayTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlayOpen_pausesSelectedSession_andClosingResumesIt() {
        val f = BrowserFixture.onPage("https://a.com/")
        var overlay by mutableStateOf(false)
        rule.setContent { BrowserRoute(viewModel = f.viewModel, overlayOpen = overlay) }
        rule.waitForIdle()
        assertFalse(f.selected().fake.isPaused)

        overlay = true
        rule.waitForIdle()
        assertTrue("page must be paused behind the overlay", f.selected().fake.isPaused)

        overlay = false
        rule.waitForIdle()
        assertFalse(f.selected().fake.isPaused)
    }

    @Test
    fun overlayOpen_fromStart_keepsSessionPaused() {
        val f = BrowserFixture.onPage("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel, overlayOpen = true) }
        rule.waitForIdle()
        assertTrue(f.selected().fake.isPaused)
    }
}
