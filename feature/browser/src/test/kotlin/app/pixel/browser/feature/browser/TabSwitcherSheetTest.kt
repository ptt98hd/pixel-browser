package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_BACK
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_COUNT
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SHEET
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SHEET_NEW
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import app.pixel.browser.feature.browser.BrowserTestTags.tabClose
import app.pixel.browser.feature.browser.BrowserTestTags.tabItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 005 – tab switcher tối thiểu: AC27–AC30. */
@RunWith(RobolectricTestRunner::class)
class TabSwitcherSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture
    private lateinit var ids: List<String>

    /** 3 tab: [0] home (title rỗng → host), [1] "Alpha" (canGoBack), [2] "Beta" (được chọn). */
    private fun launchWithThreeTabs() {
        f = BrowserFixture()
        val a = f.tabManager.openTab("https://a.com/start")
        f.tabManager.spy(a.id).apply {
            visit("https://a.com/start")
            visit("https://a.com/")
            fake.simulateTitle("Alpha")
        }
        val b = f.tabManager.openTab("https://b.com/")
        f.tabManager.spy(b.id).apply {
            fake.simulatePageFinished("https://b.com/")
            fake.simulateTitle("Beta")
        }
        // publish lại để TabManager.tabs đồng bộ title từ engine
        f.tabManager.selectTab(b.id)
        ids =
            f.tabManager.tabs.value
                .map { it.id }
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.waitForIdle()
    }

    // --- AC27 ---

    @Test
    fun ac27_listsAllTabs_inTabManagerOrder() {
        launchWithThreeTabs()
        rule.onNodeWithTag(TAB_SHEET).assertExists()
        assertEquals(3, rule.tabItemCount())
        val tops = ids.map { rule.onNodeWithTag(tabItem(it)).getBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(3, tops.distinct().size)
    }

    @Test
    fun ac27_showsTitle_orHostWhenTitleEmpty() {
        launchWithThreeTabs()
        rule.onNodeWithTag(tabItem(ids[0])).assert(hasText("google.com", substring = true))
        rule.onNodeWithTag(tabItem(ids[1])).assert(hasText("Alpha", substring = true))
        rule.onNodeWithTag(tabItem(ids[2])).assert(hasText("Beta", substring = true))
    }

    @Test
    fun ac27_selectedTab_hasSelectedSemantics() {
        launchWithThreeTabs()
        rule.onNodeWithTag(tabItem(ids[2])).assertIsSelected()
        rule.onNodeWithTag(tabItem(ids[0])).assertIsNotSelected()
        rule.onNodeWithTag(tabItem(ids[1])).assertIsNotSelected()
    }

    // --- AC28 ---

    @Test
    fun ac28_tapItem_selectsTab_closesSheet_andUpdatesChrome() {
        launchWithThreeTabs()
        rule.onNodeWithTag(tabItem(ids[0])).performClick()
        rule.waitForIdle()
        assertEquals(ids[0], f.tabManager.selectedTabId.value)
        rule.onNodeWithTag(TAB_SHEET).assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("google.com")
        rule.onNodeWithTag(BTN_BACK).assertIsNotEnabled()
    }

    // --- AC29 ---

    @Test
    fun ac29_tapClose_closesTab_itemDisappears_sheetStaysOpen() {
        launchWithThreeTabs()
        rule.onNodeWithTag(tabClose(ids[1]), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(
            listOf(ids[0], ids[2]),
            f.tabManager.tabs.value
                .map { it.id },
        )
        rule.onNodeWithTag(tabItem(ids[1])).assertDoesNotExist()
        rule.onNodeWithTag(TAB_SHEET).assertExists()
        assertEquals(2, rule.tabItemCount())
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("2")
    }

    @Test
    fun ac29_closingLastTab_leavesSingleNewHomeTab() {
        launchWithThreeTabs()
        ids.forEach {
            rule.onNodeWithTag(tabClose(it), useUnmergedTree = true).performClick()
            rule.waitForIdle()
        }
        assertEquals(1, f.tabManager.tabs.value.size)
        val remaining =
            f.tabManager.tabs.value
                .single()
                .id
        assertFalse(remaining in ids)
        rule.onNodeWithTag(TAB_SHEET).assertExists()
        assertEquals(1, rule.tabItemCount())
        rule.onNodeWithTag(tabItem(remaining)).assertExists()
    }

    // --- AC30 ---

    @Test
    fun ac30_tapNewTab_opensSelectedHomeTabAtEnd_andClosesSheet() {
        launchWithThreeTabs()
        rule.onNodeWithTag(TAB_SHEET_NEW).performClick()
        rule.waitForIdle()
        val tabs = f.tabManager.tabs.value
        assertEquals(4, tabs.size)
        assertEquals(ids, tabs.take(3).map { it.id })
        val newId = tabs.last().id
        assertNotEquals(ids[2], newId)
        assertEquals(newId, f.tabManager.selectedTabId.value)
        assertEquals(listOf(f.homeUrl), f.tabManager.spy(newId).loadUrlCalls)
        rule.onNodeWithTag(TAB_SHEET).assertDoesNotExist()
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("4")
    }
}
