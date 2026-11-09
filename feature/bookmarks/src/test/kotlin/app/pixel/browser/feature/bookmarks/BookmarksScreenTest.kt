package app.pixel.browser.feature.bookmarks

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.FolderOption
import app.pixel.browser.core.bookmarks.ROOT_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 013 AC21-AC25 – `BookmarksScreen` stateless (Robolectric + Compose). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BookmarksScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val openedFolders = mutableListOf<Long>()
    private val ups = mutableListOf<Unit>()
    private val openedUrls = mutableListOf<String>()
    private val openedNew = mutableListOf<String>()
    private val edited = mutableListOf<Long>()
    private val created = mutableListOf<String>()
    private val renamed = mutableListOf<Pair<Long, String>>()
    private val moved = mutableListOf<Pair<Long, Long>>()
    private val deleted = mutableListOf<Long>()
    private val optionRequests = mutableListOf<Long?>()
    private var closed = 0
    private var undoExpired = 0
    private var undone = 0

    private val actions =
        BookmarksActions(
            onOpenFolder = { openedFolders += it },
            onNavigateUp = {
                ups += Unit
                true
            },
            onOpenUrl = { openedUrls += it },
            onOpenInNewTab = { openedNew += it },
            onEditBookmark = { edited += it },
            onCreateFolder = { created += it },
            onRename = { id, t -> renamed += id to t },
            onMove = { id, p -> moved += id to p },
            folderOptions = { ex ->
                optionRequests += ex
                listOf(
                    FolderOption(ROOT_ID, "Dấu trang", 0),
                    FolderOption(50, "Other", 1),
                    FolderOption(51, "Nested", 2),
                ).filter { it.id != ex }
            },
            onDelete = { deleted += it },
            onUndo = { undone++ },
            onUndoExpired = { undoExpired++ },
            onClose = { closed++ },
        )

    private fun folder(
        id: Long,
        title: String = "Folder $id",
    ) = BookmarkFolder(id, ROOT_ID, title, id.toInt(), 1)

    private fun bm(
        id: Long,
        url: String = "https://site$id.example/page",
        title: String = "Site $id",
    ) = Bookmark(id, ROOT_ID, title, url, id.toInt(), 1, 1)

    private fun show(
        state: BookmarksUiState,
        a: BookmarksActions = actions,
    ) {
        rule.setContent { MaterialTheme { BookmarksScreen(state, a) } }
        rule.waitForIdle()
    }

    private fun longPress(tag: String) {
        rule.onNodeWithTag(tag).performTouchInput { longClick() }
        rule.waitForIdle()
    }

    // ---------- AC21 ----------

    @Test
    fun ac21_atRoot_titleIsShown_noUpButton() {
        show(BookmarksUiState(folderId = ROOT_ID, title = "Dấu trang"))
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertExists()
        assertEquals("Dấu trang", rule.onNodeWithTag(BookmarksTestTags.TITLE).textValue())
        rule.onNodeWithTag(BookmarksTestTags.UP).assertDoesNotExist()
    }

    @Test
    fun ac21_inSubfolder_showsFolderName_andUpButtonNavigatesUp() {
        show(BookmarksUiState(folderId = 7, title = "Tin tức"))
        assertEquals("Tin tức", rule.onNodeWithTag(BookmarksTestTags.TITLE).textValue())
        rule.onNodeWithTag(BookmarksTestTags.UP).performClick()
        assertEquals(1, ups.size)
    }

    @Test
    fun closeButton_callsOnClose() {
        show(BookmarksUiState(title = "Dấu trang"))
        rule.onNodeWithTag(BookmarksTestTags.CLOSE).performClick()
        assertEquals(1, closed)
    }

    // ---------- AC22 ----------

    @Test
    fun ac22_foldersListedBeforeBookmarks_withChildCount_hostAndTitle() {
        show(
            BookmarksUiState(
                title = "Dấu trang",
                children =
                    listOf(
                        folder(2, "Tin tức"),
                        folder(3),
                        bm(4, "https://www.kotlinlang.org/docs", "Kotlin"),
                        bm(5),
                    ),
                childCounts = mapOf(2L to 7),
            ),
        )
        val folderBottom = rule.onNodeWithTag(BookmarksTestTags.folder(3)).getBoundsInRoot().bottom
        val itemTop = rule.onNodeWithTag(BookmarksTestTags.item(4)).getBoundsInRoot().top
        assertTrue(folderBottom <= itemTop)
        assertTrue(rule.onNodeWithTag(BookmarksTestTags.folder(2)).textValue().contains("Tin tức"))
        assertTrue(rule.onNodeWithTag(BookmarksTestTags.folder(2)).textValue().contains("7"))
        val itemText = rule.onNodeWithTag(BookmarksTestTags.item(4)).textValue()
        assertTrue(itemText, itemText.contains("Kotlin") && itemText.contains("kotlinlang.org"))
    }

    @Test
    fun ac22_tapFolderOpensIt_tapBookmarkOpensUrlOnce() {
        show(BookmarksUiState(children = listOf(folder(2), bm(4, "https://k.org/x"))))
        rule.onNodeWithTag(BookmarksTestTags.folder(2)).performClick()
        assertEquals(listOf(2L), openedFolders)
        rule.onNodeWithTag(BookmarksTestTags.item(4)).performClick()
        assertEquals(listOf("https://k.org/x"), openedUrls)
    }

    @Test
    fun ac22_emptyFolderAndEmptySearch_showDifferentNonBlankMessages() {
        var state by mutableStateOf(BookmarksUiState(children = emptyList()))
        rule.setContent { MaterialTheme { BookmarksScreen(state, actions) } }
        rule.waitForIdle()
        val folderEmpty = rule.onNodeWithTag(BookmarksTestTags.EMPTY).textValue()
        state = BookmarksUiState(query = "zzz", results = emptyList())
        rule.waitForIdle()
        val searchEmpty = rule.onNodeWithTag(BookmarksTestTags.EMPTY).textValue()
        assertTrue(folderEmpty.isNotBlank() && searchEmpty.isNotBlank())
        assertNotEquals(folderEmpty, searchEmpty)
    }

    @Test
    fun ac22_bigList_isLazy() {
        show(BookmarksUiState(children = (10L..2_009L).map { bm(it) }))
        val composed = rule.onAllNodes(hasTestTagPrefix("bookmark_item_")).fetchSemanticsNodes().size
        assertTrue("composed $composed of 2000", composed in 1..200)
    }

    // ---------- AC23 ----------

    @Test
    fun ac23_searchButton_opensField_typingForwardsQuery() {
        val queries = mutableListOf<String>()
        show(BookmarksUiState(), actions.copy(onQueryChange = { queries += it }))
        rule.onNodeWithTag(BookmarksTestTags.SEARCH).performClick()
        rule.onNodeWithTag(BookmarksTestTags.SEARCH_FIELD).performTextInput("wiki")
        assertEquals("wiki", queries.last())
    }

    @Test
    fun ac23_results_areFlat_withParentFolderName_tapOpensUrl() {
        val hit = BookmarkHit(bm(9, "https://wiki.org/", "Wikipedia"), "Reading")
        show(BookmarksUiState(query = "wiki", results = listOf(hit)))
        val text = rule.onNodeWithTag(BookmarksTestTags.item(9)).textValue()
        assertTrue(text, text.contains("Wikipedia") && text.contains("Reading"))
        rule.onNodeWithTag(BookmarksTestTags.item(9)).performClick()
        assertEquals(listOf("https://wiki.org/"), openedUrls)
    }

    // ---------- AC24 ----------

    @Test
    fun ac24_longPressBookmark_showsBookmarkMenu_andDoesNotTap() {
        show(BookmarksUiState(children = listOf(bm(4))))
        longPress(BookmarksTestTags.item(4))
        listOf(
            BookmarksTestTags.MENU_EDIT,
            BookmarksTestTags.MENU_OPEN_NEW_TAB,
            BookmarksTestTags.MENU_COPY,
            BookmarksTestTags.MENU_MOVE,
            BookmarksTestTags.MENU_DELETE,
        ).forEach { rule.onNodeWithTag(it).assertExists() }
        rule.onNodeWithTag(BookmarksTestTags.MENU_RENAME).assertDoesNotExist()
        assertTrue(openedUrls.isEmpty())
    }

    @Test
    fun ac24_longPressFolder_showsFolderMenu() {
        show(BookmarksUiState(children = listOf(folder(2))))
        longPress(BookmarksTestTags.folder(2))
        listOf(BookmarksTestTags.MENU_RENAME, BookmarksTestTags.MENU_MOVE, BookmarksTestTags.MENU_DELETE)
            .forEach { rule.onNodeWithTag(it).assertExists() }
        rule.onNodeWithTag(BookmarksTestTags.MENU_EDIT).assertDoesNotExist()
        rule.onNodeWithTag(BookmarksTestTags.MENU_OPEN_NEW_TAB).assertDoesNotExist()
        assertTrue(openedFolders.isEmpty())
    }

    @Test
    fun ac24_menuEdit_openNewTab_delete_callbacks() {
        show(BookmarksUiState(children = listOf(bm(4, "https://k.org/x"))))
        longPress(BookmarksTestTags.item(4))
        rule.onNodeWithTag(BookmarksTestTags.MENU_EDIT).performClick()
        assertEquals(listOf(4L), edited)
        longPress(BookmarksTestTags.item(4))
        rule.onNodeWithTag(BookmarksTestTags.MENU_OPEN_NEW_TAB).performClick()
        assertEquals(listOf("https://k.org/x"), openedNew)
        longPress(BookmarksTestTags.item(4))
        rule.onNodeWithTag(BookmarksTestTags.MENU_DELETE).performClick()
        assertEquals(listOf(4L), deleted)
    }

    @Test
    fun ac24_menuCopy_putsUrlOnClipboard() {
        show(BookmarksUiState(children = listOf(bm(4, "https://k.org/x"))))
        longPress(BookmarksTestTags.item(4))
        rule.onNodeWithTag(BookmarksTestTags.MENU_COPY).performClick()
        rule.waitForIdle()
        val cm = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "https://k.org/x",
            cm.primaryClip
                ?.getItemAt(0)
                ?.text
                ?.toString(),
        )
    }

    @Test
    fun ac24_menuMove_listsOptionsExcludingItself_pickCallsMove() {
        show(BookmarksUiState(children = listOf(folder(50, "Other"), bm(4))))
        longPress(BookmarksTestTags.item(4))
        rule.onNodeWithTag(BookmarksTestTags.MENU_MOVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.MOVE_DIALOG).assertExists()
        assertEquals(listOf<Long?>(4L), optionRequests)
        rule.onNodeWithTag(BookmarksTestTags.folderOption(50)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(4L to 50L), moved)
    }

    @Test
    fun ac24_moveFolder_dialogDoesNotOfferItself() {
        show(BookmarksUiState(children = listOf(folder(50, "Other"))))
        longPress(BookmarksTestTags.folder(50))
        rule.onNodeWithTag(BookmarksTestTags.MENU_MOVE).performClick()
        rule.waitForIdle()
        assertEquals(listOf<Long?>(50L), optionRequests)
        rule.onNodeWithTag(BookmarksTestTags.folderOption(50)).assertDoesNotExist()
        rule.onNodeWithTag(BookmarksTestTags.folderOption(51)).assertExists()
    }

    @Test
    fun ac24_renameFolder_dialogPrefilled_confirmCallsRename() {
        show(BookmarksUiState(children = listOf(folder(2, "Cũ"))))
        longPress(BookmarksTestTags.folder(2))
        rule.onNodeWithTag(BookmarksTestTags.MENU_RENAME).performClick()
        rule.waitForIdle()
        assertEquals("Cũ", rule.onNodeWithTag(BookmarksTestTags.FOLDER_NAME_FIELD).editableText())
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_NAME_FIELD).performTextReplacement("Mới")
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_DIALOG_CONFIRM).performClick()
        rule.waitForIdle()
        assertEquals(listOf(2L to "Mới"), renamed)
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac24_renameFolder_blankName_showsError_noCallback_dialogStaysOpen() {
        show(BookmarksUiState(children = listOf(folder(2, "Cũ"))))
        longPress(BookmarksTestTags.folder(2))
        rule.onNodeWithTag(BookmarksTestTags.MENU_RENAME).performClick()
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_NAME_FIELD).performTextReplacement("   ")
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_DIALOG_CONFIRM).performClick()
        rule.waitForIdle()
        assertTrue(renamed.isEmpty())
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_DIALOG_ERROR).assertExists()
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_DIALOG).assertExists()
    }

    @Test
    fun ac24_newFolder_dialog_confirmCallsCreateFolder() {
        show(BookmarksUiState())
        rule.onNodeWithTag(BookmarksTestTags.NEW_FOLDER).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_NAME_FIELD).performTextInput("Mới")
        rule.onNodeWithTag(BookmarksTestTags.FOLDER_DIALOG_CONFIRM).performClick()
        rule.waitForIdle()
        assertEquals(listOf("Mới"), created)
    }

    // ---------- AC25 ----------

    @Test
    @Config(qualifiers = "vi-w411dp-h891dp")
    fun ac25_rows_haveAccessibilityActions_deleteAndEdit() {
        show(BookmarksUiState(children = listOf(bm(4), folder(2))))
        val item = rule.onNodeWithTag(BookmarksTestTags.item(4))
        val labels = item.customActionLabels()
        assertTrue("labels=$labels", "Xóa" in labels && "Sửa" in labels)
        rule.runOnUiThread { item.invokeCustomAction("Xóa") }
        rule.runOnUiThread { item.invokeCustomAction("Sửa") }
        assertEquals(listOf(4L), deleted)
        assertEquals(listOf(4L), edited)
        val folderLabels = rule.onNodeWithTag(BookmarksTestTags.folder(2)).customActionLabels()
        assertTrue("folder labels=$folderLabels", "Xóa" in folderLabels)
    }

    @Test
    fun ac25_pendingUndo_showsUndoAction_tapCallsOnUndo() {
        val tree =
            app.pixel.browser.core.bookmarks
                .DeletedTree(listOf(bm(4)))
        show(BookmarksUiState(pendingUndo = tree))
        rule.onNodeWithTag(BookmarksTestTags.UNDO).assertIsDisplayed()
        rule.onNodeWithTag(BookmarksTestTags.UNDO).performClick()
        rule.waitForIdle()
        assertEquals(1, undone)
    }

    @Test
    fun ac25_undoSnackbarTimingOut_callsOnUndoExpired() {
        val tree =
            app.pixel.browser.core.bookmarks
                .DeletedTree(listOf(bm(4)))
        show(BookmarksUiState(pendingUndo = tree))
        rule.mainClock.advanceTimeBy(15_000)
        rule.waitForIdle()
        assertEquals(1, undoExpired)
        assertEquals(0, undone)
    }
}
