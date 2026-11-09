package app.pixel.browser.feature.browser.tabs

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 009: trang phía sau switcher không được chạy lại khi lifecycle pause/resume. */
@RunWith(RobolectricTestRunner::class)
class TabSwitcherPauseTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun switcherOpen_lifecyclePauseResume_keepsSessionPaused() {
        val f = BrowserFixture.onPage("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        assertTrue(f.selected().fake.isPaused)

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.waitForIdle()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        assertTrue("page must stay paused behind the switcher", f.selected().fake.isPaused)
    }

    @Test
    fun switcherClosed_resumesSession() {
        val f = BrowserFixture.onPage("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.waitForIdle()
        androidx.test.espresso.Espresso
            .pressBackUnconditionally()
        rule.waitForIdle()
        assertFalse(f.selected().fake.isPaused)
    }
}
