package app.pixel.browser.feature.browser

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_SHARE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Spec 005 – AC26. */
@RunWith(RobolectricTestRunner::class)
class ShareLinkTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ac26_share_startsChooserWrappingSendTextWithUrlAndTitle() {
        val f = BrowserFixture()
        f.selected().apply {
            loadUrl("https://example.com/a")
            fake.simulateTitle("A")
            fake.simulatePageFinished("https://example.com/a")
        }
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_SHARE).assertIsEnabled().performClick()
        rule.waitForIdle()

        val started = shadowOf(rule.activity).nextStartedActivity
        assertNotNull("no activity started", started)
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        val send = started.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(send)
        assertEquals(Intent.ACTION_SEND, send!!.action)
        assertEquals("text/plain", send.type)
        assertEquals("https://example.com/a", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("A", send.getStringExtra(Intent.EXTRA_SUBJECT))
    }

    private fun shareDisabledFor(url: String) {
        rule.setContent { BrowserScreen(state = BrowserUiState(url = url, tabCount = 1), actions = BrowserActions()) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_SHARE).assertIsNotEnabled()
    }

    @Test
    fun ac26_share_disabledForAboutBlank() = shareDisabledFor("about:blank")

    @Test
    fun ac26_share_disabledForEmptyUrl() = shareDisabledFor("")
}
