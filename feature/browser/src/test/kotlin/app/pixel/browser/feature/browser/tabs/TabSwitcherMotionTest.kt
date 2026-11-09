package app.pixel.browser.feature.browser.tabs

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.BrowserTestTags.tabItem
import app.pixel.browser.feature.browser.spy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 009 AC18 – animation mở/chọn của tab switcher (đồng hồ Compose tắt auto-advance). */
@RunWith(RobolectricTestRunner::class)
class TabSwitcherMotionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture
    private lateinit var ids: List<String>

    private fun launch() {
        f = BrowserFixture.onPage("https://a.com/")
        val b = f.tabManager.openTab("https://b.com/", select = false)
        f.tabManager
            .spy(b.id)
            .fake
            .simulatePageFinished("https://b.com/")
        f.tabManager.selectTab(BrowserFixture.PAGE_TAB_ID)
        ids =
            f.tabManager.tabs.value
                .map { it.id }
        rule.setContent {
            BrowserRoute(viewModel = f.viewModel, homeContent = { Box(Modifier.fillMaxSize().testTag("home_page")) })
        }
        rule.waitForIdle()
    }

    private fun openAndSettle() {
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac18_tapCard_switcherStaysComposedWhileCardExpands_thenDisappearsWithin1000ms() {
        launch()
        openAndSettle()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(tabItem(ids[1])).performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        assertEquals(ids[1], f.tabManager.selectedTabId.value)

        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
    }

    @Test
    fun ac18_opening_switcherExistsFromFirstFrame_selectedCardFullyShownAfter1000ms() {
        launch()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()

        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag(tabItem(ids[0])).assertIsDisplayed()
    }

    @Test
    fun ac18_noAnimationBlocksInputForMoreThan600ms_whileOpening() {
        launch()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag(tabItem(ids[1])).performClick()

        assertEquals(ids[1], f.tabManager.selectedTabId.value)
    }
}
