package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_CONTAINER
import app.pixel.browser.feature.browser.BrowserTestTags.ENGINE_PLACEHOLDER
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 005 – AC2/AC4 với WebViewEngineSession thật (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class BrowserScreenWebViewTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun ac4_switchingTabsRepeatedly_reattachesViews_withoutParentConflict() {
        val tabManager = TabManager({ WebViewEngineSession(rule.activity) }, InMemoryStore())
        val vm = BrowserViewModel(tabManager)
        // Spec 010: tab home không hiển thị engine, nên dùng hai tab trang web để kiểm tra gắn/gỡ WebView.
        val first = tabManager.openTab("https://a.com/").id
        val second = tabManager.openTab("https://b.com/").id
        rule.setContent { BrowserRoute(viewModel = vm) }
        rule.waitForIdle()

        fun webView(id: String) = (tabManager.session(id) as WebViewEngineSession).webView

        repeat(3) {
            listOf(first, second).forEach { selected ->
                rule.runOnIdle { vm.onSelectTab(selected) }
                rule.waitForIdle()
                val other = if (selected == first) second else first
                val shown = webView(selected)
                assertNotNull("selected WebView must be attached", shown.parent)
                assertTrue(shown.isAttachedToWindow)
                assertNull("previous WebView must be detached", webView(other).parent)
                rule.onNodeWithTag(ENGINE_CONTAINER, useUnmergedTree = true).assertExists()
                rule.onNodeWithTag(ENGINE_PLACEHOLDER, useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }
}
