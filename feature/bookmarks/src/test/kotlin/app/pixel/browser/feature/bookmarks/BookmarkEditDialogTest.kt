package app.pixel.browser.feature.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.DeletedTree
import app.pixel.browser.core.bookmarks.FolderOption
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 013 AC20 – `BookmarkEditDialog` (stateless) và `BookmarkEditRoute` (stateful). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BookmarkEditDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val options =
        listOf(FolderOption(ROOT_ID, "Dấu trang", 0), FolderOption(10, "News", 1), FolderOption(11, "Deep", 2))
    private val saved = mutableListOf<Triple<String, String, Long>>()
    private var removed = 0
    private var dismissed = 0

    private fun showDialog(
        error: BookmarkEditError? = null,
        title: String = "Kotlin",
        url: String = "https://kotlinlang.org/",
        folder: Long = 10,
    ) {
        rule.setContent {
            MaterialTheme {
                BookmarkEditDialog(
                    title = title,
                    url = url,
                    folderId = folder,
                    options = options,
                    error = error,
                    onSave = { t, u, f -> saved += Triple(t, u, f) },
                    onRemove = { removed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun ac20_prefilledFields_andCurrentFolder() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_DIALOG).assertExists()
        assertEquals("Kotlin", rule.onNodeWithTag(BookmarksTestTags.EDIT_TITLE_FIELD).editableText())
        assertEquals("https://kotlinlang.org/", rule.onNodeWithTag(BookmarksTestTags.EDIT_URL_FIELD).editableText())
        assertTrue(rule.onNodeWithTag(BookmarksTestTags.EDIT_FOLDER_PICKER).textValue().contains("News"))
    }

    @Test
    fun ac20_folderPicker_listsIndentedOptions_choosingChangesFolder_andSavePassesIt() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_FOLDER_PICKER).performClick()
        rule.waitForIdle()
        val rootLeft = rule.onNodeWithTag(BookmarksTestTags.folderOption(ROOT_ID)).getBoundsInRoot().left
        val deepLeft = rule.onNodeWithTag(BookmarksTestTags.folderOption(11)).getBoundsInRoot().left
        assertTrue("deep option must be indented", deepLeft > rootLeft)
        rule.onNodeWithTag(BookmarksTestTags.folderOption(11)).performClick()
        rule.waitForIdle()
        assertTrue(rule.onNodeWithTag(BookmarksTestTags.EDIT_FOLDER_PICKER).textValue().contains("Deep"))
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).performClick()
        assertEquals(listOf(Triple("Kotlin", "https://kotlinlang.org/", 11L)), saved)
    }

    @Test
    fun ac20_save_passesEditedValues() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_TITLE_FIELD).performTextReplacement("Mới")
        rule.onNodeWithTag(BookmarksTestTags.EDIT_URL_FIELD).performTextReplacement("https://b.com/")
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).performClick()
        assertEquals(listOf(Triple("Mới", "https://b.com/", 10L)), saved)
    }

    @Test
    fun ac20_error_isShown_dialogStaysOpen() {
        showDialog(error = BookmarkEditError.InvalidUrl)
        rule.onNodeWithTag(BookmarksTestTags.EDIT_ERROR).assertExists()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_DIALOG).assertExists()
        assertEquals(0, dismissed)
    }

    @Test
    fun ac20_noError_noErrorNode() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_ERROR).assertDoesNotExist()
    }

    @Test
    fun ac20_save_disabledWhenUrlEmpty() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).assertIsEnabled()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_URL_FIELD).performTextReplacement("")
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).assertIsNotEnabled()
    }

    @Test
    fun ac20_remove_callsOnRemove() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_REMOVE).performClick()
        assertEquals(1, removed)
    }

    @Test
    fun ac20_cancelAndBack_dismissWithoutSaving() {
        showDialog()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_CANCEL).performClick()
        assertEquals(1, dismissed)
        Espresso.pressBack()
        rule.waitForIdle()
        assertTrue(dismissed >= 2)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun ac20_typedText_survivesStateRestoration() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            MaterialTheme {
                BookmarkEditDialog("Kotlin", "https://k.org/", 10, options, null, { _, _, _ -> }, {}, {})
            }
        }
        rule.onNodeWithTag(BookmarksTestTags.EDIT_TITLE_FIELD).performTextReplacement("Đã sửa")
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("Đã sửa", rule.onNodeWithTag(BookmarksTestTags.EDIT_TITLE_FIELD).editableText())
    }

    // ---------- BookmarkEditRoute ----------

    private val repo = SpyBookmarkRepository()

    private fun showRoute(bookmarkId: Long) {
        rule.setContent {
            MaterialTheme {
                BookmarkEditRoute(repo, bookmarkId, onDismiss = { dismissed++ }, onRemoved = { removedTrees += it })
            }
        }
        rule.waitForIdle()
    }

    private val removedTrees = mutableListOf<DeletedTree>()

    @Test
    fun ac20_route_loadsBookmark_saveCallsUpdateOnce_andDismisses() {
        val f = repo.addFolder("News")
        val b = repo.addBm("https://a.com/", "A")
        showRoute(b.id)
        assertEquals("A", rule.onNodeWithTag(BookmarksTestTags.EDIT_TITLE_FIELD).editableText())
        rule.onNodeWithTag(BookmarksTestTags.EDIT_TITLE_FIELD).performTextReplacement("B")
        rule.onNodeWithTag(BookmarksTestTags.EDIT_URL_FIELD).performTextReplacement("https://b.com/")
        rule.onNodeWithTag(BookmarksTestTags.EDIT_FOLDER_PICKER).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.folderOption(f.id)).performClick()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).performClick()
        rule.waitForIdle()
        assertEquals(listOf(listOf<Any>(b.id, "B", "https://b.com/", f.id)), repo.updateCalls)
        assertEquals(1, dismissed)
        val stored = runBlocking { repo.bookmark(b.id) }!!
        assertEquals("B", stored.title)
        assertEquals(f.id, stored.parentId)
    }

    @Test
    fun ac20_route_invalidUrl_showsError_staysOpen_dataUnchanged() {
        val b = repo.addBm("https://a.com/", "A")
        showRoute(b.id)
        rule.onNodeWithTag(BookmarksTestTags.EDIT_URL_FIELD).performTextReplacement("javascript:alert(1)")
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.EDIT_ERROR).assertExists()
        assertEquals(0, dismissed)
        assertEquals(b, runBlocking { repo.bookmark(b.id) })
    }

    @Test
    fun ac20_route_remove_deletesAndReportsTree_thenDismisses() {
        val b = repo.addBm("https://a.com/", "A")
        showRoute(b.id)
        rule.onNodeWithTag(BookmarksTestTags.EDIT_REMOVE).performClick()
        rule.waitForIdle()
        assertEquals(listOf(b.id), repo.deleteCalls)
        assertEquals(1, removedTrees.size)
        assertEquals(b.id, removedTrees.single().rootId)
        assertEquals(1, dismissed)
        assertTrue(runBlocking { repo.observeChildren(ROOT_ID).first() }.isEmpty())
    }

    @Test
    fun ac20_route_bookmarkDeletedMeanwhile_saveFailsWithNotFound_reportsErrorOrDismisses() {
        val b = repo.addBm("https://a.com/", "A")
        showRoute(b.id)
        runBlocking { repo.delete(b.id) }
        rule.onNodeWithTag(BookmarksTestTags.EDIT_SAVE).performClick()
        rule.waitForIdle()
        val hasError = rule.onAllNodesWithTag(BookmarksTestTags.EDIT_ERROR).fetchSemanticsNodes().isNotEmpty()
        assertTrue("must show an error or dismiss", hasError || dismissed == 1)
        val result: BookmarkResult<Bookmark> = runBlocking { repo.updateBookmark(b.id, "x", "https://a.com/", ROOT_ID) }
        assertEquals(BookmarkResult.NotFound, result)
    }
}
