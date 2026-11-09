package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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

/**
 * Review spec 005: session được chọn pause/resume theo lifecycle; đổi tab pause tab cũ; đóng tab gỡ view.
 * Spec 010 AC18: ở home, `EngineContainer` bị gỡ nên session của tab chọn bị pause; rời home → resume.
 *
 * Spec 010: tab home không hiển thị engine, nên các test lifecycle dùng tab trang web.
 */
@RunWith(RobolectricTestRunner::class)
class SessionLifecycleTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedSession_followsActivityLifecycle() {
        val f = BrowserFixture.onPage("https://a.com/")
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
        val f = BrowserFixture.onPage("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        val first = f.selected()
        rule.runOnIdle { f.tabManager.openTab("https://b.com/") }
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
        val page = tabManager.openTab("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = vm) }
        rule.waitForIdle()
        val id = page.id
        val webView = (tabManager.session(id) as WebViewEngineSession).webView
        assertTrue(webView.isAttachedToWindow)

        rule.runOnIdle { vm.onCloseTab(id) }
        rule.waitForIdle()

        assertNull(webView.parent)
    }

    // --- Spec 010 AC18 ---

    @Test
    fun ac18_atHome_selectedSessionIsPaused_andEngineNotComposed() {
        val f = BrowserFixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel, homeContent = { Box(Modifier.testTag("home")) }) }
        rule.waitForIdle()
        rule.onNodeWithTag("home").assertExists()
        rule.onNodeWithTag(BrowserTestTags.ENGINE_CONTAINER, useUnmergedTree = true).assertDoesNotExist()
        assertTrue(f.selected().fake.isPaused)
    }

    @Test
    fun ac18_pressHome_pausesPage_andLeavingHomeResumesIt() {
        val f = BrowserFixture.onPage("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel, homeContent = { Box(Modifier.testTag("home")) }) }
        rule.waitForIdle()
        val s = f.selected()
        assertFalse(s.fake.isPaused)

        rule.runOnIdle { f.viewModel.onHome() }
        rule.waitForIdle()
        rule.onNodeWithTag("home").assertExists()
        assertTrue(s.fake.isPaused)
        assertFalse(s.fake.isClosed)

        rule.runOnIdle { f.viewModel.onBack() }
        rule.waitForIdle()
        rule.onNodeWithTag("home").assertDoesNotExist()
        rule.onNodeWithTag(BrowserTestTags.ENGINE_CONTAINER, useUnmergedTree = true).assertExists()
        assertFalse(s.fake.isPaused)
    }

    @Test
    fun ac18_openUrlFromHome_resumesSession() {
        val f = BrowserFixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel, homeContent = { Box(Modifier.testTag("home")) }) }
        rule.waitForIdle()
        rule.runOnIdle { f.viewModel.onOpenUrl("https://a.com/") }
        rule.waitForIdle()
        rule.onNodeWithTag("home").assertDoesNotExist()
        assertFalse(f.selected().fake.isPaused)
    }
}
