package app.pixel.browser.feature.browser.gesture

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.BrowserActions
import app.pixel.browser.feature.browser.BrowserFixture
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserScreen
import app.pixel.browser.feature.browser.BrowserTestTags.BOTTOM_BAR
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_TABS
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_RELOAD
import app.pixel.browser.feature.browser.BrowserTestTags.TAB_SWITCHER
import app.pixel.browser.feature.browser.BrowserUiState
import app.pixel.browser.feature.browser.InMemoryStore
import app.pixel.browser.feature.browser.TabItem
import app.pixel.browser.feature.browser.customActionLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 008 AC12, AC14, AC17 – vuốt bottom bar đổi tab kề. */
@RunWith(RobolectricTestRunner::class)
class BottomBarSwipeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private val deltas = mutableListOf<Int>()
    private val actions =
        BrowserActions(
            onBack = { calls += "back" },
            onForward = { calls += "forward" },
            onHome = { calls += "home" },
            onNewTab = { calls += "newTab" },
            onReload = { calls += "reload" },
            onScrollToTop = { calls += "top" },
            onScrollToBottom = { calls += "bottom" },
            onStartEditing = { calls += "edit" },
            onSelectAdjacentTab = { deltas += it },
        )

    private fun threeTabs(
        selected: String = "b",
        isEditingUrl: Boolean = false,
    ) = BrowserUiState(
        url = "https://b.com/",
        canGoBack = true,
        canGoForward = true,
        tabCount = 3,
        tabs =
            listOf(
                TabItem("a", "A", "https://a.com/"),
                TabItem("b", "B", "https://b.com/"),
                TabItem("c", "C", "https://c.com/"),
            ),
        selectedTabId = selected,
        isEditingUrl = isEditingUrl,
    )

    private fun show(
        state: BrowserUiState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                BrowserScreen(state = state, actions = actions, systemGestureNav = false)
            }
        }
        rule.waitForIdle()
    }

    private fun swipeBarLeft() = rule.onNodeWithTag(BOTTOM_BAR).performTouchInput { swipeLeft() }

    private fun swipeBarRight() = rule.onNodeWithTag(BOTTOM_BAR).performTouchInput { swipeRight() }

    // --- AC12: UI báo delta ---

    @Test
    fun ac12_swipeLeft_requestsNextTab() {
        show(threeTabs("b"))
        swipeBarLeft()
        assertEquals(listOf(+1), deltas)
    }

    @Test
    fun ac12_swipeRight_requestsPreviousTab() {
        show(threeTabs("b"))
        swipeBarRight()
        assertEquals(listOf(-1), deltas)
    }

    @Test
    fun ac12_rtl_reversesDirection() {
        show(threeTabs("b"), LayoutDirection.Rtl)
        swipeBarLeft()
        assertEquals(listOf(-1), deltas)
        deltas.clear()
        swipeBarRight()
        assertEquals(listOf(+1), deltas)
    }

    @Test
    fun ac12_swipe_doesNotTriggerButtonsOrOpenMenuOrSwitcher() {
        show(threeTabs("b"))
        swipeBarLeft()
        swipeBarRight()
        assertTrue("unexpected button callbacks $calls", calls.isEmpty())
        rule.onNodeWithTag(MENU_RELOAD).assertDoesNotExist()
        rule.onNodeWithTag(TAB_SWITCHER).assertDoesNotExist()
    }

    // --- AC12: nối với ViewModel thật ---

    private fun launchRoute(selected: String): BrowserFixture {
        val store =
            InMemoryStore().apply {
                stored =
                    TabsSnapshot(
                        listOf(
                            TabSnapshot("a", "https://a.com/"),
                            TabSnapshot("b", "https://b.com/"),
                            TabSnapshot("c", "https://c.com/"),
                        ),
                        selected,
                    )
            }
        val f = BrowserFixture(store)
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        return f
    }

    @Test
    fun ac12_route_swipeLeftFromB_selectsC() {
        val f = launchRoute("b")
        swipeBarLeft()
        rule.waitForIdle()
        assertEquals("c", f.tabManager.selectedTabId.value)
    }

    @Test
    fun ac12_route_swipeRightFromB_selectsA() {
        val f = launchRoute("b")
        swipeBarRight()
        rule.waitForIdle()
        assertEquals("a", f.tabManager.selectedTabId.value)
    }

    @Test
    fun ac12_route_atFirstTab_swipeRight_staysOnA() {
        val f = launchRoute("a")
        swipeBarRight()
        rule.waitForIdle()
        assertEquals("a", f.tabManager.selectedTabId.value)
    }

    @Test
    fun ac12_route_atLastTab_swipeLeft_staysOnC() {
        val f = launchRoute("c")
        swipeBarLeft()
        rule.waitForIdle()
        assertEquals("c", f.tabManager.selectedTabId.value)
    }

    // --- AC14: accessibility ---

    private fun string(name: String): String {
        val id = rule.activity.resources.getIdentifier(name, "string", rule.activity.packageName)
        assertNotEquals("missing string resource $name", 0, id)
        return rule.activity.getString(id)
    }

    private fun tabsActionLabels() = rule.onNodeWithTag(BTN_TABS).customActionLabels()

    private fun invokeTabsAction(label: String): Boolean {
        var result = false
        rule.runOnIdle {
            val action =
                rule
                    .onNodeWithTag(BTN_TABS)
                    .fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions]
                    .first { it.label == label }
            result = action.action()
        }
        return result
    }

    @Test
    fun ac14_middleTab_exposesPreviousAndNextActions_afterLongPressAction() {
        show(threeTabs("b"))
        val previous = string("browser_previous_tab")
        val next = string("browser_next_tab")
        val labels = tabsActionLabels()
        assertTrue(labels.toString(), previous in labels && next in labels)
        assertEquals("long-press action must stay first (spec 005 AC23)", string("browser_new_tab"), labels.first())
    }

    @Test
    fun ac14_firstTab_hasNoPreviousAction() {
        show(threeTabs("a"))
        val labels = tabsActionLabels()
        assertFalse(string("browser_previous_tab") in labels)
        assertTrue(string("browser_next_tab") in labels)
    }

    @Test
    fun ac14_lastTab_hasNoNextAction() {
        show(threeTabs("c"))
        val labels = tabsActionLabels()
        assertTrue(string("browser_previous_tab") in labels)
        assertFalse(string("browser_next_tab") in labels)
    }

    @Test
    fun ac14_actions_selectAdjacentTabs() {
        show(threeTabs("b"))
        assertTrue(invokeTabsAction(string("browser_next_tab")))
        assertTrue(invokeTabsAction(string("browser_previous_tab")))
        assertEquals(listOf(+1, -1), deltas)
    }

    // --- AC17 ---

    @Test
    fun ac17_editingUrl_swipeDoesNotChangeTab() {
        show(threeTabs("b", isEditingUrl = true))
        swipeBarLeft()
        swipeBarRight()
        assertTrue(deltas.isEmpty())
    }
}
