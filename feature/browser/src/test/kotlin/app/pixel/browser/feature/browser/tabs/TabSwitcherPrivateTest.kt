package app.pixel.browser.feature.browser.tabs

import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.tabs.Tab
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.CLOSE_PRIVATE_CONFIRM
import app.pixel.browser.feature.browser.BrowserTestTags.CLOSE_PRIVATE_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_COUNT
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_CLOSE_PRIVATE
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_GROUP_NORMAL
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_GROUP_PRIVATE
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_NEW
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_PRIVATE_EMPTY
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_UNDO
import app.pixel.browser.feature.browser.BrowserTestTags.tabClose
import app.pixel.browser.feature.browser.BrowserTestTags.tabItem
import app.pixel.browser.feature.browser.spy
import app.pixel.browser.feature.browser.textValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Spec 016 AC19–AC20 – trình chuyển tab hai nhóm (thường / ẩn danh), qua `BrowserRoute` + Fake. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class TabSwitcherPrivateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture
    private lateinit var normalA: String
    private lateinit var normalB: String
    private var private1: Tab? = null
    private var private2: Tab? = null

    /** Hai tab thường (A đang chọn, B) và [privateTabs] tab ẩn danh (không chọn). */
    private fun launch(
        privateTabs: Int = 1,
        available: Boolean = true,
        selectPrivate: Boolean = false,
    ) {
        f = BrowserFixture.onPage("https://a.com/", privateAvailable = { available })
        normalA = BrowserFixture.PAGE_TAB_ID
        val b = f.tabManager.openTab("https://b.com/", select = false)
        f.tabManager
            .spy(b.id)
            .fake
            .simulatePageFinished("https://b.com/")
        normalB = b.id
        if (privateTabs >= 1) private1 = openPrivate("https://p1.example/")
        if (privateTabs >= 2) private2 = openPrivate("https://p2.example/")
        if (selectPrivate) f.tabManager.selectTab(private1!!.id)
        f.tabManager.refresh()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun openPrivate(url: String): Tab {
        val t = f.tabManager.openTab(url, select = false, isPrivate = true)
        f.tabManager
            .spy(t.id)
            .fake
            .simulatePageFinished(url)
        return t
    }

    private fun openSwitcher() {
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun assertCards(
        shown: List<String>,
        hidden: List<String>,
    ) {
        shown.forEach { rule.onNodeWithTag(tabItem(it)).assertExists() }
        hidden.forEach { rule.onNodeWithTag(tabItem(it)).assertDoesNotExist() }
    }

    // --- AC19 ---

    @Test
    fun ac19_defaultGroup_isTheGroupOfSelectedNormalTab() {
        launch()
        openSwitcher()
        assertCards(shown = listOf(normalA, normalB), hidden = listOf(private1!!.id))
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_NORMAL).assertIsSelected()
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_PRIVATE).assertIsNotSelected()
    }

    @Test
    fun ac19_defaultGroup_isPrivate_whenSelectedTabIsPrivate() {
        launch(selectPrivate = true)
        openSwitcher()
        assertCards(shown = listOf(private1!!.id), hidden = listOf(normalA, normalB))
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_PRIVATE).assertIsSelected()
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_NORMAL).assertIsNotSelected()
    }

    @Test
    fun ac19_groupButtons_haveTabRole() {
        launch()
        openSwitcher()
        listOf(TAB_SWITCHER_GROUP_NORMAL, TAB_SWITCHER_GROUP_PRIVATE).forEach {
            assertEquals(
                Role.Tab,
                rule.onNodeWithTag(it).fetchSemanticsNode().config[SemanticsProperties.Role],
            )
        }
    }

    @Test
    fun ac19_tapGroups_filterCardsByPrivacy() {
        launch(privateTabs = 2)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        assertCards(shown = listOf(private1!!.id, private2!!.id), hidden = listOf(normalA, normalB))
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_PRIVATE).assertIsSelected()
        tap(TAB_SWITCHER_GROUP_NORMAL)
        assertCards(shown = listOf(normalA, normalB), hidden = listOf(private1!!.id, private2!!.id))
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_NORMAL).assertIsSelected()
    }

    @Test
    fun ac19_emptyPrivateGroup_showsEmptyState() {
        launch(privateTabs = 0)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        rule.onNodeWithTag(TAB_SWITCHER_PRIVATE_EMPTY).assertExists()
        tap(TAB_SWITCHER_GROUP_NORMAL)
        rule.onNodeWithTag(TAB_SWITCHER_PRIVATE_EMPTY).assertDoesNotExist()
    }

    @Test
    fun ac19_privateGroup_isHidden_whenPrivateIsUnavailableAndNoPrivateTabs() {
        launch(privateTabs = 0, available = false)
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        rule.onNodeWithTag(TAB_SWITCHER_GROUP_PRIVATE).assertDoesNotExist()
    }

    @Test
    fun ac19_newTabFab_inPrivateGroup_createsPrivateTab() {
        launch(selectPrivate = false)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        tap(TAB_SWITCHER_NEW)
        assertTrue(f.state().selectedIsPrivate)
        assertEquals(2, f.state().tabs.count { it.isPrivate })
        assertEquals(2, f.state().tabs.count { !it.isPrivate })
    }

    @Test
    fun ac19_newTabFab_inNormalGroup_createsNormalTab_evenWhenSelectedTabIsPrivate() {
        launch(selectPrivate = true)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_NORMAL)
        tap(TAB_SWITCHER_NEW)
        assertFalse(f.state().selectedIsPrivate)
        assertEquals(3, f.state().tabs.count { !it.isPrivate })
        assertEquals(1, f.state().tabs.count { it.isPrivate })
    }

    @Test
    fun ac19_selectingCardOfOtherGroup_changesSelectedTab() {
        launch()
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        tap(tabItem(private1!!.id))
        assertEquals(private1!!.id, f.state().selectedTabId)
        assertTrue(f.state().selectedIsPrivate)
    }

    @Test
    fun ac19_tabCount_countsOnlyGroupOfSelectedTab() {
        launch(privateTabs = 1)
        assertEquals("2", rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).textValue())
        rule.runOnIdle { f.viewModel.onSelectTab(private1!!.id) }
        rule.waitForIdle()
        assertEquals("1", rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).textValue())
    }

    @Test
    fun ac19_switchingGroup_isReportedToViewModel() {
        launch()
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        assertTrue(f.state().switcherShowsPrivate)
        tap(TAB_SWITCHER_GROUP_NORMAL)
        assertFalse(f.state().switcherShowsPrivate)
    }

    // --- AC20 ---

    @Test
    fun ac20_closePrivateButton_isShownOnlyInPrivateGroupWithTabs() {
        launch()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_PRIVATE).assertDoesNotExist()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_PRIVATE).assertExists()
    }

    @Test
    fun ac20_closePrivateButton_isAbsent_whenPrivateGroupIsEmpty() {
        launch(privateTabs = 0)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_PRIVATE).assertDoesNotExist()
    }

    @Test
    fun ac20_closePrivate_asksForConfirmation_thenClosesAllPrivateTabs_withoutUndo() {
        launch(privateTabs = 2)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        tap(TAB_SWITCHER_CLOSE_PRIVATE)
        rule.onNodeWithTag(CLOSE_PRIVATE_DIALOG).assertExists()
        assertEquals("nothing closed before confirming", 2, f.state().tabs.count { it.isPrivate })

        tap(CLOSE_PRIVATE_CONFIRM)

        assertEquals(0, f.state().tabs.count { it.isPrivate })
        assertEquals(2, f.state().tabs.count { !it.isPrivate })
        assertFalse(f.state().lastCloseUndoable)
        rule.onNodeWithTag(TAB_UNDO).assertDoesNotExist()
    }

    @Test
    fun ac20_closingLastPrivateTabWithX_showsNoUndo() {
        launch(privateTabs = 1)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        tap(tabClose(private1!!.id))
        rule.onNodeWithTag(TAB_UNDO).assertDoesNotExist()
        assertEquals(0, f.state().tabs.count { it.isPrivate })
    }

    @Test
    fun ac20_closingNonLastPrivateTabWithX_stillOffersUndo() {
        launch(privateTabs = 2)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        tap(tabClose(private1!!.id))
        rule.onNodeWithTag(TAB_UNDO).assertExists()
    }

    @Test
    fun ac20_closingPrivateTabs_postsNoSystemNotification() {
        launch(privateTabs = 2)
        openSwitcher()
        tap(TAB_SWITCHER_GROUP_PRIVATE)
        tap(TAB_SWITCHER_CLOSE_PRIVATE)
        tap(CLOSE_PRIVATE_CONFIRM)
        val manager = RuntimeEnvironment.getApplication().getSystemService(Context.NOTIFICATION_SERVICE)
        assertEquals(0, shadowOf(manager as NotificationManager).size())
    }
}
