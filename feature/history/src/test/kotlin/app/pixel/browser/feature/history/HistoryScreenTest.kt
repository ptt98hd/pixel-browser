package app.pixel.browser.feature.history

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import app.pixel.browser.core.history.HistoryEntry
import app.pixel.browser.core.history.HistoryRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Spec 012 AC15–AC18 – `HistoryScreen` stateless (Robolectric + Compose). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HistoryScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val now = System.currentTimeMillis()
    private val dayMs = 86_400_000L

    private val opened = mutableListOf<String>()
    private val openedNew = mutableListOf<String>()
    private val deleted = mutableListOf<Long>()
    private val cleared = mutableListOf<HistoryRange>()
    private val queries = mutableListOf<String>()
    private var loadMore = 0
    private var closed = 0

    private val actions =
        HistoryActions(
            onQueryChange = { queries += it },
            onOpenUrl = { opened += it },
            onOpenInNewTab = { openedNew += it },
            onDelete = { deleted += it },
            onClear = { cleared += it },
            onLoadMore = { loadMore++ },
            onClose = { closed++ },
        )

    private fun entry(
        id: Long,
        url: String = "https://site$id.example/page",
        title: String = "Site $id",
        at: Long = now - id * 1000,
    ) = HistoryEntry(id, url, title, at)

    private fun show(state: HistoryUiState) {
        rule.setContent { MaterialTheme { HistoryScreen(state, actions) } }
        rule.waitForIdle()
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().top

    // --- AC15 ---

    @Test
    fun ac15_showsScreen_dayGroupsAndItemsNewestFirst() {
        val today = entry(1)
        val yesterday = entry(2, at = now - dayMs - 1000)
        show(stateOf(listOf(today, yesterday), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.SCREEN).assertIsDisplayed()
        rule.onNodeWithTag(HistoryTestTags.day(LocalDate.now(zone))).assertExists()
        rule.onNodeWithTag(HistoryTestTags.day(LocalDate.now(zone).minusDays(1))).assertExists()
        assertTrue(top(HistoryTestTags.item(1)) < top(HistoryTestTags.item(2)))
    }

    @Test
    fun ac15_dayLabels_inEnglish() {
        show(stateOf(listOf(entry(1), entry(2, at = now - dayMs - 1000)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.day(LocalDate.now(zone))).assertTextContains("Today")
        rule.onNodeWithTag(HistoryTestTags.day(LocalDate.now(zone).minusDays(1))).assertTextContains("Yesterday")
    }

    @Test
    @Config(qualifiers = "vi-w411dp-h891dp")
    fun ac15_dayLabels_inVietnamese() {
        show(stateOf(listOf(entry(1), entry(2, at = now - dayMs - 1000)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.day(LocalDate.now(zone))).assertTextContains("Hôm nay")
        rule.onNodeWithTag(HistoryTestTags.day(LocalDate.now(zone).minusDays(1))).assertTextContains("Hôm qua")
    }

    @Test
    fun ac15_item_showsTitleAndHost_atLeast48dpTall() {
        show(stateOf(listOf(entry(1, url = "https://www.example.com/a/b?q=1", title = "Example title")), zone = zone))
        val item = rule.onNodeWithTag(HistoryTestTags.item(1))
        item.assertTextContains("Example title")
        item.assertTextContains("example.com")
        item.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun ac15_item_withEmptyTitle_fallsBackToHost() {
        show(stateOf(listOf(entry(1, url = "https://only-host.example/p", title = "")), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.item(1)).assertTextContains("only-host.example")
    }

    @Test
    fun ac15_empty_showsEmptyView_withDifferentTextForNoMatch() {
        var state by mutableStateOf(HistoryUiState(isEmpty = true))
        rule.setContent { MaterialTheme { HistoryScreen(state, actions) } }
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.EMPTY).assertIsDisplayed()
        val noHistory = rule.onNodeWithTag(HistoryTestTags.EMPTY).textValue()
        state = HistoryUiState(query = "zzz", isEmpty = true)
        rule.waitForIdle()
        val noMatch = rule.onNodeWithTag(HistoryTestTags.EMPTY).textValue()
        assertTrue(noHistory.isNotBlank() && noMatch.isNotBlank())
        assertNotEquals(noHistory, noMatch)
    }

    @Test
    fun ac15_notEmpty_hasNoEmptyView() {
        show(stateOf(listOf(entry(1)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun ac15_loadMore_onlyWhenCanLoadMore_andInvokesCallback() {
        show(stateOf(listOf(entry(1)), canLoadMore = false, zone = zone))
        rule.onNodeWithTag(HistoryTestTags.LOAD_MORE).assertDoesNotExist()
    }

    @Test
    fun ac15_loadMore_shown_andTapCallsOnLoadMore() {
        show(stateOf(listOf(entry(1)), canLoadMore = true, zone = zone))
        rule.onNodeWithTag(HistoryTestTags.LOAD_MORE).performClick()
        assertEquals(1, loadMore)
    }

    @Test
    fun ac15_thousandItems_areLazilyComposed() {
        show(stateOf(List(1000) { entry(it + 1L, at = now - it * 1000L) }, zone = zone))
        val composed =
            rule
                .onAllNodes(hasTestTagPrefix("history_item_"))
                .fetchSemanticsNodes()
                .size
        assertTrue("composed=$composed", composed in 1..99)
    }

    // --- AC16 ---

    @Test
    fun ac16_typing_callsOnQueryChangeOncePerChange() {
        show(stateOf(listOf(entry(1)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.SEARCH).performTextInput("a")
        rule.onNodeWithTag(HistoryTestTags.SEARCH).performTextInput("b")
        assertEquals(listOf("a", "ab"), queries)
    }

    @Test
    fun ac16_clearButton_onlyWithText_andClearsIt() {
        show(stateOf(listOf(entry(1)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.SEARCH_CLEAR).assertDoesNotExist()
        rule.onNodeWithTag(HistoryTestTags.SEARCH).performTextInput("abc")
        rule.onNodeWithTag(HistoryTestTags.SEARCH_CLEAR).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals("", queries.last())
        rule.onNodeWithTag(HistoryTestTags.SEARCH_CLEAR).assertDoesNotExist()
    }

    @Test
    fun ac16_search_usesSearchImeAction() {
        show(stateOf(listOf(entry(1)), zone = zone))
        rule
            .onNodeWithTag(HistoryTestTags.SEARCH)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Search))
    }

    @Test
    fun ac16_searchText_survivesStateRestoration() {
        val tester = StateRestorationTester(rule)
        tester.setContent { MaterialTheme { HistoryScreen(stateOf(listOf(entry(1)), zone = zone), actions) } }
        rule.onNodeWithTag(HistoryTestTags.SEARCH).performTextInput("kotlin")
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SEARCH).assertTextContains("kotlin")
    }

    @Test
    fun ac16_reopen_searchFieldStartsFromAppliedQuery_soItMatchesTheList() {
        // Mở lại Lịch sử khi ViewModel (cấp Activity) còn truy vấn cũ: ô nhập phải hiện đúng truy vấn đó.
        show(stateOf(listOf(entry(1)), query = "kotlin", zone = zone))
        rule.onNodeWithTag(HistoryTestTags.SEARCH).assertTextContains("kotlin")
        rule.onNodeWithTag(HistoryTestTags.SEARCH_CLEAR).assertIsDisplayed()
    }

    @Test
    fun ac16_openSearchCloseReopen_showsSameQueryAsList() {
        var visible by mutableStateOf(true)
        var applied by mutableStateOf("")
        rule.setContent {
            MaterialTheme {
                if (visible) {
                    HistoryScreen(
                        stateOf(listOf(entry(1)), query = applied, zone = zone),
                        actions.copy(onQueryChange = { applied = it }),
                    )
                }
            }
        }
        rule.onNodeWithTag(HistoryTestTags.SEARCH).performTextInput("wiki")
        visible = false
        rule.waitForIdle()
        visible = true
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.SEARCH).assertTextContains("wiki")
    }

    // --- AC17 ---

    @Test
    fun ac17_tapItem_callsOnOpenUrlOnce() {
        show(stateOf(listOf(entry(1, url = "https://a.example/x")), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.item(1)).performClick()
        assertEquals(listOf("https://a.example/x"), opened)
    }

    private fun longPress(id: Long) {
        rule.onNodeWithTag(HistoryTestTags.item(id)).performTouchInput { longClick() }
        rule.waitForIdle()
    }

    @Test
    fun ac17_longPress_showsMenu_withoutTriggeringTap() {
        show(stateOf(listOf(entry(1)), zone = zone))
        longPress(1)
        rule.onNodeWithTag(HistoryTestTags.MENU_OPEN_NEW_TAB).assertIsDisplayed()
        rule.onNodeWithTag(HistoryTestTags.MENU_COPY).assertIsDisplayed()
        rule.onNodeWithTag(HistoryTestTags.MENU_DELETE).assertIsDisplayed()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun ac17_menuOpenInNewTab_callsCallback() {
        show(stateOf(listOf(entry(1, url = "https://a.example/x")), zone = zone))
        longPress(1)
        rule.onNodeWithTag(HistoryTestTags.MENU_OPEN_NEW_TAB).performClick()
        assertEquals(listOf("https://a.example/x"), openedNew)
    }

    @Test
    fun ac17_menuCopy_putsUrlOnClipboard() {
        show(stateOf(listOf(entry(1, url = "https://a.example/x")), zone = zone))
        longPress(1)
        rule.onNodeWithTag(HistoryTestTags.MENU_COPY).performClick()
        rule.waitForIdle()
        val cm = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "https://a.example/x",
            cm.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString(),
        )
    }

    @Test
    fun ac17_menuDelete_callsOnDelete() {
        show(stateOf(listOf(entry(7)), zone = zone))
        longPress(7)
        rule.onNodeWithTag(HistoryTestTags.MENU_DELETE).performClick()
        assertEquals(listOf(7L), deleted)
    }

    @Test
    @Config(qualifiers = "vi-w411dp-h891dp")
    fun ac17_accessibilityActions_deleteAndOpenInNewTab() {
        show(stateOf(listOf(entry(3, url = "https://a.example/x")), zone = zone))
        val item = rule.onNodeWithTag(HistoryTestTags.item(3))
        val labels = item.customActionLabels()
        assertTrue("labels=$labels", "Xóa" in labels && "Mở trong tab mới" in labels)
        rule.runOnUiThread { item.invokeCustomAction("Xóa") }
        rule.runOnUiThread { item.invokeCustomAction("Mở trong tab mới") }
        assertEquals(listOf(3L), deleted)
        assertEquals(listOf("https://a.example/x"), openedNew)
    }

    // --- AC18 ---

    private fun openClearDialog() {
        rule.onNodeWithTag(HistoryTestTags.CLEAR_BUTTON).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac18_clearDialog_hasFourRanges_defaultLastHourSelected() {
        show(stateOf(listOf(entry(1)), zone = zone))
        openClearDialog()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_DIALOG).assertIsDisplayed()
        rule.onNodeWithTag(HistoryTestTags.clearRange(HistoryRange.LastHour)).assertIsSelected()
        listOf(HistoryRange.LastDay, HistoryRange.LastWeek, HistoryRange.All).forEach {
            rule.onNodeWithTag(HistoryTestTags.clearRange(it)).assertIsNotSelected()
        }
    }

    @Test
    fun ac18_confirm_callsOnClearWithSelectedRange_once_andClosesDialog() {
        show(stateOf(listOf(entry(1)), zone = zone))
        openClearDialog()
        rule.onNodeWithTag(HistoryTestTags.clearRange(HistoryRange.All)).performClick()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_CONFIRM).performClick()
        rule.waitForIdle()
        assertEquals(listOf(HistoryRange.All), cleared)
        rule.onNodeWithTag(HistoryTestTags.CLEAR_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac18_confirmWithDefault_clearsLastHour() {
        show(stateOf(listOf(entry(1)), zone = zone))
        openClearDialog()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_CONFIRM).performClick()
        assertEquals(listOf(HistoryRange.LastHour), cleared)
    }

    @Test
    fun ac18_cancel_closesWithoutCallback() {
        show(stateOf(listOf(entry(1)), zone = zone))
        openClearDialog()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_CANCEL).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_DIALOG).assertDoesNotExist()
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun ac18_back_closesDialogWithoutCallback() {
        show(stateOf(listOf(entry(1)), zone = zone))
        openClearDialog()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_DIALOG).assertExists()
        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithTag(HistoryTestTags.CLEAR_DIALOG).assertDoesNotExist()
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun ac18_clearButton_disabledWhenEmptyAndNoQuery_enabledOtherwise() {
        show(HistoryUiState(isEmpty = true))
        rule.onNodeWithTag(HistoryTestTags.CLEAR_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun ac18_clearButton_enabledWithEntries() {
        show(stateOf(listOf(entry(1)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.CLEAR_BUTTON).assertIsEnabled()
    }

    @Test
    fun ac18_clearButton_enabledWhenQueryPresentEvenIfNoMatch() {
        show(HistoryUiState(query = "zzz", isEmpty = true))
        rule.onNodeWithTag(HistoryTestTags.CLEAR_BUTTON).assertIsEnabled()
    }

    // --- Close ---

    @Test
    fun close_button_callsOnClose() {
        show(stateOf(listOf(entry(1)), zone = zone))
        rule.onNodeWithTag(HistoryTestTags.CLOSE).performClick()
        assertEquals(1, closed)
    }
}
