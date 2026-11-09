package app.pixel.browser.feature.browser.tabs

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.espresso.Espresso
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.CLOSE_ALL_CANCEL
import app.pixel.browser.feature.browser.BrowserTestTags.CLOSE_ALL_CONFIRM
import app.pixel.browser.feature.browser.BrowserTestTags.CLOSE_ALL_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_COUNT
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_CLOSE_ALL
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER_NEW
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_UNDO
import app.pixel.browser.feature.browser.BrowserTestTags.URL_DISPLAY
import app.pixel.browser.feature.browser.BrowserTestTags.tabClose
import app.pixel.browser.feature.browser.BrowserTestTags.tabFavicon
import app.pixel.browser.feature.browser.BrowserTestTags.tabFaviconFallback
import app.pixel.browser.feature.browser.BrowserTestTags.tabItem
import app.pixel.browser.feature.browser.BrowserTestTags.tabThumb
import app.pixel.browser.feature.browser.BrowserTestTags.tabThumbPlaceholder
import app.pixel.browser.feature.browser.FakeTabThumbnailRepository
import app.pixel.browser.feature.browser.R
import app.pixel.browser.feature.browser.TabItem
import app.pixel.browser.feature.browser.assertTextIs
import app.pixel.browser.feature.browser.customActionLabels
import app.pixel.browser.feature.browser.spy
import app.pixel.browser.feature.browser.tabItemCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec 009 – AC12–AC17: tab switcher toàn màn hình (thay `TabSwitcherSheetTest`, AC27–AC30 của spec 005).
 * Phần nội dung thẻ (AC13) kiểm trực tiếp `TabSwitcher`; còn lại qua `BrowserRoute` + Fake.
 */
@RunWith(RobolectricTestRunner::class)
class TabSwitcherTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val thumbs = FakeTabThumbnailRepository()
    private lateinit var f: BrowserFixture

    /** id các tab: [0] "Alpha" (tab đang chọn), [1] "Beta", [2] không title (host c.com). */
    private lateinit var ids: List<String>

    private fun launchRoute(extraTabs: Int = 0) {
        f = BrowserFixture.onPage("https://a.com/", thumbnails = thumbs)
        f.selected().fake.simulateTitle("Alpha")
        val b = f.tabManager.openTab("https://b.com/", select = false)
        f.tabManager.spy(b.id).fake.run {
            simulatePageFinished("https://b.com/")
            simulateTitle("Beta")
        }
        val c = f.tabManager.openTab("https://c.com/", select = false)
        f.tabManager
            .spy(c.id)
            .fake
            .simulatePageFinished("https://c.com/")
        repeat(extraTabs) { f.tabManager.openTab("https://extra$it.com/") }
        f.tabManager.refresh()
        ids =
            f.tabManager.tabs.value
                .map { it.id }
        setRouteContent()
    }

    private fun setRouteContent() {
        rule.setContent {
            BrowserRoute(
                viewModel = f.viewModel,
                homeContent = { Box(Modifier.fillMaxSize().testTag("home_page")) },
            )
        }
        rule.waitForIdle()
    }

    private fun openSwitcher() {
        rule.onNodeWithTag(BTN_TABS).performClick()
        rule.waitForIdle()
    }

    /** Bounds của root compose của Activity (root đầu tiên; các dialog/popup là root khác). */
    private fun activityRootBounds() = rule.onAllNodes(isRoot())[0].getBoundsInRoot()

    private fun selectedId() = f.tabManager.selectedTabId.value

    private fun tabIds() =
        f.tabManager.tabs.value
            .map { it.id }

    // --- AC12: mở toàn màn hình ---

    @Test
    fun ac12_tapTabs_showsSwitcher_coveringWholeRoot_includingBars() {
        launchRoute()
        openSwitcher()

        val root = activityRootBounds()
        val switcher = rule.onNodeWithTag(TAB_SWITCHER).getBoundsInRoot()
        assertEquals(root.left, switcher.left)
        assertEquals(root.top, switcher.top)
        assertEquals(root.right, switcher.right)
        assertEquals(root.bottom, switcher.bottom)
    }

    @Test
    fun ac12_tapTabs_callsOnOpenTabSwitcherExactlyOnce() {
        launchRoute()
        openSwitcher()
        // ViewModel chụp tab hiện tại đúng một lần khi mở (AC8).
        assertEquals(1, thumbs.captures.size)
        assertEquals(ids[0], thumbs.captures.single().tabId)
    }

    @Test
    fun ac12_everyTabHasACard() {
        launchRoute()
        openSwitcher()
        assertEquals(3, rule.tabItemCount())
        ids.forEach { rule.onNodeWithTag(tabItem(it)).assertExists() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun ac12_phoneWidth_has2Columns_inTabManagerOrder() {
        launchRoute(extraTabs = 1)
        openSwitcher()
        val bounds = ids.map { rule.onNodeWithTag(tabItem(it)).getBoundsInRoot() }
        assertEquals(4, bounds.size)
        // hàng i/2, cột i%2
        assertEquals(bounds[0].left, bounds[2].left)
        assertEquals(bounds[1].left, bounds[3].left)
        assertTrue(bounds[0].left < bounds[1].left)
        assertEquals(bounds[0].top, bounds[1].top)
        assertEquals(bounds[2].top, bounds[3].top)
        assertTrue(bounds[0].top < bounds[2].top)
    }

    @Test
    @Config(qualifiers = "w840dp-h891dp")
    fun ac12_wideWidth_has4Columns() {
        launchRoute(extraTabs = 1)
        openSwitcher()
        val bounds = ids.map { rule.onNodeWithTag(tabItem(it)).getBoundsInRoot() }
        assertEquals(1, bounds.map { it.top }.distinct().size)
        assertEquals(bounds.map { it.left }.sorted(), bounds.map { it.left })
        assertEquals(4, bounds.map { it.left }.distinct().size)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun ac12_with30Tabs_selectedLastTab_isScrolledIntoViewOnOpen() {
        launchRoute(extraTabs = 27)
        assertEquals(30, ids.size)
        assertEquals(ids.last(), selectedId())
        openSwitcher()
        rule.onNodeWithTag(tabItem(ids.last())).assertIsDisplayed()
    }

    // --- AC13: nội dung thẻ (TabSwitcher trực tiếp) ---

    private val swatch: ImageBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun show(
        tabs: List<TabItem>,
        selected: String? = tabs.firstOrNull()?.id,
        thumbnails: Set<String> = emptySet(),
        favicons: Set<String> = emptySet(),
    ) {
        rule.setContent {
            TabSwitcher(
                tabs = tabs,
                selectedTabId = selected,
                thumbnail = { id -> swatch.takeIf { id in thumbnails } },
                favicon = { id -> swatch.takeIf { id in favicons } },
                onSelect = {},
                onClose = {},
                onUndoClose = {},
                onNewTab = {},
                onCloseAll = {},
                onDismiss = {},
            )
        }
        rule.waitForIdle()
    }

    private val titled = TabItem("t1", "Alpha", "https://a.com/")
    private val untitled = TabItem("t2", "", "https://b.com/page")
    private val home = TabItem("h", "", "", isHome = true)

    @Test
    fun ac13_title_isTitle_thenHost_thenNewTabStringForHome() {
        show(listOf(titled, untitled, home))
        rule.onNodeWithTag(tabItem("t1")).assert(hasText("Alpha", substring = true))
        rule.onNodeWithTag(tabItem("t2")).assert(hasText("b.com", substring = true))
        val newTab = rule.activity.getString(R.string.browser_untitled_tab)
        rule.onNodeWithTag(tabItem("h")).assert(hasText(newTab, substring = true))
    }

    @Test
    fun ac13_thumbnail_isShownWhenAvailable_otherwisePlaceholder() {
        show(listOf(titled, untitled), thumbnails = setOf("t1"))
        rule.onNodeWithTag(tabThumb("t1"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(tabThumbPlaceholder("t1"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(tabThumb("t2"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(tabThumbPlaceholder("t2"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun ac13_homeTab_alwaysShowsPlaceholder_evenIfAThumbnailExists() {
        show(listOf(titled, home), thumbnails = setOf("h"))
        rule.onNodeWithTag(tabThumbPlaceholder("h"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(tabThumb("h"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac13_favicon_isShownWhenAvailable_otherwiseFirstLetterOfHost() {
        show(listOf(titled, untitled), favicons = setOf("t1"))
        rule.onNodeWithTag(tabFavicon("t1"), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(tabFaviconFallback("t1"), useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(tabFavicon("t2"), useUnmergedTree = true).assertDoesNotExist()
        val letter = hasText("b", ignoreCase = true)
        rule
            .onNodeWithTag(tabFaviconFallback("t2"), useUnmergedTree = true)
            .assert(letter or hasAnyDescendant(letter))
    }

    @Test
    fun ac13_homeTab_fallbackFavicon_isAnIconWithoutLetter() {
        show(listOf(titled, home))
        rule.onNodeWithTag(tabFaviconFallback("h"), useUnmergedTree = true).assertExists()
        rule
            .onAllNodes(
                hasAnyAncestor(hasTestTag(tabFaviconFallback("h"))) and hasText("", substring = true),
                useUnmergedTree = true,
            ).assertCountEquals(0)
    }

    @Test
    fun ac13_selectedTab_hasSelectedSemantics_othersDoNot() {
        show(listOf(titled, untitled, home), selected = "t2")
        rule.onNodeWithTag(tabItem("t2")).assertIsSelected()
        rule.onNodeWithTag(tabItem("t1")).assertIsNotSelected()
        rule.onNodeWithTag(tabItem("h")).assertIsNotSelected()
    }

    @Test
    fun ac13_closeButton_describesTheTab_andHasAtLeast48dpTouchTarget() {
        show(listOf(titled, untitled))
        val close = rule.onNodeWithTag(tabClose("t1"), useUnmergedTree = true)
        close.assert(hasContentDescription("Alpha", substring = true))
        close.assertWidthIsAtLeast(48.dp)
        close.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun ac13_closeButton_ofUntitledTab_describesItWithHost() {
        show(listOf(untitled))
        rule
            .onNodeWithTag(tabClose("t2"), useUnmergedTree = true)
            .assert(hasContentDescription("b.com", substring = true))
    }

    @Test
    fun ac13_longTitle_staysInASingleCard_withoutCrashing() {
        val long = TabItem("t3", "عنوان طويل جدا 🙂 ".repeat(40), "https://long.example/")
        show(listOf(long))
        rule.onNodeWithTag(tabItem("t3")).assertIsDisplayed()
    }

    // --- AC14: chọn thẻ ---

    @Test
    fun ac14_tapCard_selectsImmediately_thenSwitcherClosesAndUrlShowsHost() {
        launchRoute()
        openSwitcher()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(tabItem(ids[1])).performClick()
        assertEquals("selection must change before the animation ends", ids[1], selectedId())

        rule.mainClock.advanceTimeBy(1000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
        rule.onNodeWithTag(URL_DISPLAY).assertTextIs("b.com")
    }

    // --- AC15: đóng bằng nút / vuốt + hoàn tác ---

    @Test
    fun ac15_tapClose_removesCard_decrementsCount_andShowsUndo() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabClose(ids[1]), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        assertEquals(listOf(ids[0], ids[2]), tabIds())
        rule.onNodeWithTag(tabItem(ids[1])).assertDoesNotExist()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("2")
        rule.onNodeWithTag(TAB_UNDO).assertExists()
    }

    @Test
    fun ac15_tapUndo_restoresTabInPlace_hidesUndo_keepsSelection() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabClose(ids[1]), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_UNDO).performClick()
        rule.waitForIdle()

        assertEquals(ids, tabIds())
        assertEquals(ids[0], selectedId())
        rule.onNodeWithTag(tabItem(ids[1])).assertExists()
        rule.onNodeWithTag(TAB_UNDO).assertDoesNotExist()
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("3")
    }

    @Test
    fun ac15_swipeLeft_closesTabLikeTheCloseButton() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabItem(ids[1])).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(listOf(ids[0], ids[2]), tabIds())
        rule.onNodeWithTag(tabItem(ids[1])).assertDoesNotExist()
        rule.onNodeWithTag(TAB_UNDO).assertExists()
    }

    @Test
    fun ac15_swipeRight_closesTabLikeTheCloseButton() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabItem(ids[2])).performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertEquals(listOf(ids[0], ids[1]), tabIds())
        rule.onNodeWithTag(TAB_UNDO).assertExists()
    }

    @Test
    fun ac15_shortSlowSwipe_snapsBack_withoutClosing() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabItem(ids[1])).performTouchInput {
            // Kéo 20% chiều rộng thẻ, chậm (không fling), rồi thả.
            swipe(
                start = center + Offset(width * 0.1f, 0f),
                end = center - Offset(width * 0.1f, 0f),
                durationMillis = 1000,
            )
        }
        rule.waitForIdle()

        assertEquals(ids, tabIds())
        rule.onNodeWithTag(tabItem(ids[1])).assertExists()
        rule.onNodeWithTag(TAB_UNDO).assertDoesNotExist()
    }

    @Test
    fun ac15_closingTwoTabsInARow_undoRestoresTheLastClosedOne() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabClose(ids[1]), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(tabClose(ids[2]), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(listOf(ids[0]), tabIds())
        assertEquals(1, rule.onAllNodesWithTag(TAB_UNDO).fetchSemanticsNodes().size)

        rule.onNodeWithTag(TAB_UNDO).performClick()
        rule.waitForIdle()

        assertEquals(listOf(ids[0], ids[2]), tabIds())
    }

    @Test
    fun ac15_closingTheLastTab_leavesOneNewHomeCard_switcherStaysOpen_undoRestoresIt() {
        f = BrowserFixture.onPage("https://a.com/", thumbnails = thumbs)
        ids = listOf(BrowserFixture.PAGE_TAB_ID)
        setRouteContent()
        openSwitcher()

        rule.onNodeWithTag(tabClose(ids[0]), useUnmergedTree = true).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        assertEquals(1, rule.tabItemCount())
        val newId = tabIds().single()
        assertNotEquals(ids[0], newId)
        assertTrue(
            f.tabManager.tabs.value
                .single()
                .isHome,
        )
        rule.onNodeWithTag(tabItem(newId)).assertExists()

        rule.onNodeWithTag(TAB_UNDO).performClick()
        rule.waitForIdle()

        assertEquals(ids, tabIds())
        assertEquals(ids[0], selectedId())
        rule.onNodeWithTag(tabItem(ids[0])).assertExists()
        rule.onNodeWithTag(tabItem(newId)).assertDoesNotExist()
    }

    @Test
    fun ac15_eachCard_hasACloseAccessibilityAction_thatClosesTheTab() {
        launchRoute()
        openSwitcher()
        val labels = rule.onNodeWithTag(tabItem(ids[1])).customActionLabels()
        assertTrue("expected a 'close' custom action, was $labels", labels.any { it.contains("close", true) })

        rule.runOnUiThread {
            val node = rule.onNodeWithTag(tabItem(ids[1])).fetchSemanticsNode()
            val actions =
                node.config[SemanticsActions.CustomActions]
            actions.first { it.label.contains("close", true) }.action()
        }
        rule.waitForIdle()

        assertEquals(listOf(ids[0], ids[2]), tabIds())
    }

    @Test
    fun edge_swipingTwoCardsQuickly_closesEachExactlyOnce() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabItem(ids[1])).performTouchInput { swipeLeft(durationMillis = 80) }
        rule.onNodeWithTag(tabItem(ids[2])).performTouchInput { swipeRight(durationMillis = 80) }
        rule.waitForIdle()

        assertEquals(listOf(ids[0]), tabIds())
        assertEquals(setOf(ids[1], ids[2]), thumbs.removed.toSet())
        assertEquals(2, thumbs.removed.size)
    }

    @Test
    fun edge_undoSnackbar_disappearsWhenSwitcherCloses() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(tabClose(ids[1]), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(tabItem(ids[0])).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
        rule.onNodeWithTag(TAB_UNDO).assertDoesNotExist()
        // Ngăn xếp undo vẫn còn.
        assertEquals(listOf(ids[0], ids[2]), tabIds())
        rule.runOnIdle { f.viewModel.onUndoCloseTab() }
        assertEquals(ids, tabIds())
    }

    @Test
    fun edge_switcherStaysOpenAfterStateRestoration() {
        f = BrowserFixture.onPage("https://a.com/", thumbnails = thumbs)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
    }

    // --- AC16: tab mới ---

    @Test
    fun ac16_newTabFab_isBottomEnd_aboveNavigationBar() {
        launchRoute()
        openSwitcher()
        val root = activityRootBounds()
        val fab = rule.onNodeWithTag(TAB_SWITCHER_NEW).getBoundsInRoot()
        assertTrue(fab.left > root.width / 2)
        assertTrue(fab.top > root.height / 2)
        assertTrue(fab.bottom <= root.bottom)
    }

    @Test
    fun ac16_tapNewTab_appendsSelectedHomeTab_closesSwitcher_showsHomePage() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_NEW).performClick()
        rule.waitForIdle()

        val tabs = f.tabManager.tabs.value
        assertEquals(4, tabs.size)
        assertEquals(ids, tabs.take(3).map { it.id })
        assertTrue(tabs.last().isHome)
        assertEquals(tabs.last().id, selectedId())
        assertTrue(
            f.tabManager
                .spy(tabs.last().id)
                .loadUrlCalls
                .isEmpty(),
        )
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
        rule.onNodeWithTag("home_page").assertExists()
        rule.onNodeWithTag(TAB_COUNT, useUnmergedTree = true).assertTextIs("4")
    }

    // --- AC17: đóng tất cả ---

    @Test
    fun ac17_tapCloseAll_showsConfirmationDialog() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_ALL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(CLOSE_ALL_DIALOG).assertExists()
        assertEquals(ids, tabIds())
    }

    @Test
    fun ac17_cancel_closesDialog_keepsTabs() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_ALL).performClick()
        rule.onNodeWithTag(CLOSE_ALL_CANCEL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(CLOSE_ALL_DIALOG).assertDoesNotExist()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        assertEquals(ids, tabIds())
    }

    @Test
    fun ac17_back_closesDialogOnly_keepsTabsAndSwitcher() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_ALL).performClick()
        rule.waitForIdle()
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()
        rule.onNodeWithTag(CLOSE_ALL_DIALOG).assertDoesNotExist()
        rule.onNodeWithTag(TAB_SWITCHER).assertExists()
        assertEquals(ids, tabIds())
    }

    @Test
    fun ac17_confirm_closesAllTabs_closesSwitcher_leavesOneSelectedHomeTab() {
        launchRoute()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_ALL).performClick()
        rule.onNodeWithTag(CLOSE_ALL_CONFIRM).performClick()
        rule.waitForIdle()

        val tabs = f.tabManager.tabs.value
        assertEquals(1, tabs.size)
        assertTrue(tabs.single().isHome)
        assertEquals(tabs.single().id, selectedId())
        assertTrue(tabs.single().id !in ids)
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
        rule.onNodeWithTag(CLOSE_ALL_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac17_closeAll_isDisabled_whenOnlyAHomeTabExists() {
        f = BrowserFixture(thumbnails = thumbs)
        ids = tabIds()
        setRouteContent()
        openSwitcher()
        rule.onNodeWithTag(TAB_SWITCHER_CLOSE_ALL).assertIsNotEnabled()
    }
}
