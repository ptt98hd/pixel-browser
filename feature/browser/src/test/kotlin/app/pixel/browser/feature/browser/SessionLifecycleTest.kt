package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.tabs.TabManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Review spec 005: session được chọn pause/resume theo lifecycle; đổi tab pause tab cũ; đóng tab gỡ view. */
@RunWith(RobolectricTestRunner::class)
class SessionLifecycleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedSession_followsActivityLifecycle() {
        val f = BrowserFixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        assertFalse(f.selected().fake.isPaused)

        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.waitForIdle()
        assertTrue(f.selected().fake.isPaused)

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        assertFalse(f.selected().fake.isPaused)
    }

    @Test
    fun switchingTabs_pausesPrevious_andResumesNew() {
        val f = BrowserFixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        val first = f.selected()
        rule.runOnIdle { f.viewModel.onNewTab() }
        rule.waitForIdle()
        val second = f.selected()
        assertTrue(first.fake.isPaused)
        assertFalse(second.fake.isPaused)

        rule.runOnIdle {
            f.viewModel.onSelectTab(
                f.tabManager.tabs.value
                    .first()
                    .id,
            )
        }
        rule.waitForIdle()
        assertFalse(first.fake.isPaused)
        assertTrue(second.fake.isPaused)
    }

    @Test
    fun closingSelectedAttachedWebViewTab_detachesItsView() {
        val tabManager = TabManager({ WebViewEngineSession(rule.activity) }, InMemoryStore())
        val vm = BrowserViewModel(tabManager)
        rule.setContent { BrowserRoute(viewModel = vm) }
        rule.waitForIdle()
        val id = tabManager.selectedTabId.value!!
        val webView = (tabManager.session(id) as WebViewEngineSession).webView
        assertTrue(webView.isAttachedToWindow)

        rule.runOnIdle { vm.onCloseTab(id) }
        rule.waitForIdle()

        assertNull(webView.parent)
    }
}
