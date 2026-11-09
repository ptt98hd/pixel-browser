package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.BOOKMARK_EDIT_ACTION
import app.pixel.browser.feature.browser.BrowserTestTags.BOOKMARK_INDICATOR
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_BOOKMARK
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_BOOKMARKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 013 AC19 – menu "Thêm/Sửa dấu trang", "Dấu trang", chỉ báo ngôi sao và snackbar "Sửa". */
@RunWith(RobolectricTestRunner::class)
class BrowserMenuBookmarkTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var toggled = 0
    private var openedBookmarks = 0
    private val edited = mutableListOf<Long>()
    private var shown = 0

    private val actions =
        BrowserActions(
            onToggleBookmark = { toggled++ },
            onOpenBookmarks = { openedBookmarks++ },
            onEditBookmark = { edited += it },
            onMessageShown = { shown++ },
        )

    private val page = BrowserUiState(url = "https://a.com/", tabCount = 1, canBookmark = true)

    private fun show(state: BrowserUiState) {
        rule.setContent { BrowserScreen(state = state, actions = actions) }
    }

    private fun showMenu(state: BrowserUiState) {
        show(state)
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
    }

    @Test
    fun ac19_menuBookmark_unsaved_existsAndEnabled() {
        showMenu(page)
        rule.onNodeWithTag(MENU_BOOKMARK).assertExists().assertIsEnabled()
    }

    @Test
    fun ac19_menuBookmark_saved_hasEditLabel_differentFromAddLabel() {
        var state by mutableStateOf(page)
        rule.setContent { BrowserScreen(state = state, actions = actions) }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
        val add = rule.onNodeWithTag(MENU_BOOKMARK).textValue()
        state = page.copy(isBookmarked = true, bookmarkId = 5)
        rule.waitForIdle()
        val edit = rule.onNodeWithTag(MENU_BOOKMARK).textValue()
        assertNotEquals(add, edit)
    }

    @Test
    fun ac19_menuBookmark_disabledWhenCannotBookmark() {
        showMenu(BrowserUiState(url = "about:blank", tabCount = 1, canBookmark = false))
        rule.onNodeWithTag(MENU_BOOKMARK).assertExists().assertIsNotEnabled()
    }

    @Test
    fun ac19_menuBookmark_disabledAtHome() {
        showMenu(BrowserUiState(isHome = true, tabCount = 1, canBookmark = false))
        rule.onNodeWithTag(MENU_BOOKMARK).assertIsNotEnabled()
    }

    @Test
    fun ac19_tapMenuBookmark_callsToggleOnce_andClosesMenu() {
        showMenu(page)
        rule.onNodeWithTag(MENU_BOOKMARK).performClick()
        rule.waitForIdle()
        assertEquals(1, toggled)
        rule.onNodeWithTag(MENU_BOOKMARK).assertDoesNotExist()
    }

    @Test
    fun ac19_tapMenuBookmark_whenSaved_stillCallsToggle() {
        showMenu(page.copy(isBookmarked = true, bookmarkId = 5))
        rule.onNodeWithTag(MENU_BOOKMARK).performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun ac19_menuBookmarks_alwaysEnabled_opensScreen_andClosesMenu() {
        showMenu(BrowserUiState(isHome = true, tabCount = 1))
        rule.onNodeWithTag(MENU_BOOKMARKS).assertExists().assertIsEnabled()
        rule.onNodeWithTag(MENU_BOOKMARKS).performClick()
        rule.waitForIdle()
        assertEquals(1, openedBookmarks)
        rule.onNodeWithTag(MENU_BOOKMARKS).assertDoesNotExist()
    }

    @Test
    fun ac19_indicator_shownWhenBookmarked_andNotEditing() {
        show(page.copy(isBookmarked = true, bookmarkId = 5))
        rule.onNodeWithTag(BOOKMARK_INDICATOR).assertExists()
    }

    @Test
    fun ac19_indicator_absentWhenNotBookmarked_atHome_orEditingUrl() {
        show(page)
        rule.onNodeWithTag(BOOKMARK_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun ac19_indicator_absentWhileEditingUrl() {
        show(page.copy(isBookmarked = true, bookmarkId = 5, isEditingUrl = true))
        rule.onNodeWithTag(BOOKMARK_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun ac19_indicator_absentAtHome() {
        show(BrowserUiState(isHome = true, tabCount = 1, isBookmarked = true, bookmarkId = 5))
        rule.onNodeWithTag(BOOKMARK_INDICATOR).assertDoesNotExist()
    }

    @Test
    fun ac19_bookmarkAddedSnackbar_hasEditAction_callingOnEditBookmarkWithId() {
        show(page.copy(isBookmarked = true, bookmarkId = 42, message = BrowserMessages.BOOKMARK_ADDED))
        rule.waitForIdle()
        rule.onNodeWithTag(BOOKMARK_EDIT_ACTION).assertExists().performClick()
        rule.waitForIdle()
        assertEquals(listOf(42L), edited)
        assertEquals(1, shown)
    }
}
