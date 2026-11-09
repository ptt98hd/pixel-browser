package app.pixel.browser.feature.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

/** Spec 013 AC21 (Back), AC22 (mở URL), AC25 (xóa + hoàn tác end-to-end) – `BookmarksRoute` với ViewModel thật. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BookmarksRouteTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val repo = InMemoryBookmarkRepository()
    private val opened = mutableListOf<String>()
    private val openedNew = mutableListOf<String>()
    private var closed = 0

    private fun show() {
        val vm = BookmarksViewModel(repo, FakeBookmarkFileGateway(), { 0L }, ZoneId.of("UTC"), Dispatchers.Unconfined)
        rule.setContent {
            MaterialTheme {
                BookmarksRoute(
                    vm,
                    onOpenUrl = { opened += it },
                    onOpenInNewTab = { openedNew += it },
                    onClose = { closed++ },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun idle() = rule.waitForIdle()

    @Test
    fun ac21_systemBack_inSubfolder_goesUpOneLevel_atRoot_closes() {
        val f = repo.addFolder("F")
        repo.addBm("https://a.com/", "A", f.id)
        show()
        rule.onNodeWithTag(BookmarksTestTags.folder(f.id)).performClick()
        idle()
        assertEquals("F", rule.onNodeWithTag(BookmarksTestTags.TITLE).textValue())
        Espresso.pressBack()
        idle()
        assertEquals("Dấu trang", rule.onNodeWithTag(BookmarksTestTags.TITLE).textValue())
        assertEquals(0, closed)
        Espresso.pressBack()
        idle()
        assertEquals(1, closed)
    }

    @Test
    fun ac23_backWhileSearching_returnsToFolderList_notClose() {
        repo.addBm("https://wiki.org/", "Wikipedia")
        show()
        rule.onNodeWithTag(BookmarksTestTags.SEARCH).performClick()
        idle()
        Espresso.pressBack()
        idle()
        assertEquals(0, closed)
        rule.onNodeWithTag(BookmarksTestTags.SEARCH_FIELD).assertDoesNotExist()
    }

    @Test
    fun ac22_tapBookmark_callsOnOpenUrlOnce() {
        val b = repo.addBm("https://a.com/x", "A")
        show()
        rule.onNodeWithTag(BookmarksTestTags.item(b.id)).performClick()
        idle()
        assertEquals(listOf("https://a.com/x"), opened)
    }

    @Test
    fun ac24_menuOpenInNewTab_callsOnOpenInNewTab() {
        val b = repo.addBm("https://a.com/x", "A")
        show()
        rule.onNodeWithTag(BookmarksTestTags.item(b.id)).performTouchInput { longClick() }
        idle()
        rule.onNodeWithTag(BookmarksTestTags.MENU_OPEN_NEW_TAB).performClick()
        idle()
        assertEquals(listOf("https://a.com/x"), openedNew)
    }

    @Test
    fun ac25_delete_removesRowAtOnce_undoBringsItBackInSamePlace() {
        val a = repo.addBm("https://a.com/", "A")
        val f = repo.addFolder("F")
        repo.addBm("https://x.com/", "X", f.id)
        val b = repo.addBm("https://b.com/", "B")
        show()
        rule.onNodeWithTag(BookmarksTestTags.folder(f.id)).performTouchInput { longClick() }
        idle()
        rule.onNodeWithTag(BookmarksTestTags.MENU_DELETE).performClick()
        idle()
        rule.onNodeWithTag(BookmarksTestTags.folder(f.id)).assertDoesNotExist()
        rule.onNodeWithTag(BookmarksTestTags.UNDO).performClick()
        idle()
        rule.onNodeWithTag(BookmarksTestTags.folder(f.id)).assertExists()
        val order = runBlocking { repo.observeChildren(ROOT_ID).first() }.map { it.id }
        assertEquals(listOf(f.id, a.id, b.id), order)
        assertEquals(1, runBlocking { repo.observeChildren(f.id).first() }.size)
    }

    @Test
    fun ac25_secondDelete_whileFirstSnackbarShown_firstIsNoLongerUndoable() {
        val a = repo.addBm("https://a.com/", "A")
        val b = repo.addBm("https://b.com/", "B")
        show()
        for (id in listOf(a.id, b.id)) {
            rule.onNodeWithTag(BookmarksTestTags.item(id)).performTouchInput { longClick() }
            idle()
            rule.onNodeWithTag(BookmarksTestTags.MENU_DELETE).performClick()
            idle()
        }
        rule.onNodeWithTag(BookmarksTestTags.UNDO).performClick()
        idle()
        val titles = runBlocking { repo.observeChildren(ROOT_ID).first() }.map { it.title }
        assertTrue("titles=$titles", titles == listOf("B"))
    }
}
