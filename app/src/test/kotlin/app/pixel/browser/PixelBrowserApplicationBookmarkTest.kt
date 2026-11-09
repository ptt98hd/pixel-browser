package app.pixel.browser

import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.bookmarks.ROOT_ID
import app.pixel.browser.core.bookmarks.RoomBookmarkRepository
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.omnibox.SuggestionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 013 – `:app` nối kho dấu trang (`bookmarks.db`) và provider gợi ý (AC28 phần wiring). */
@RunWith(RobolectricTestRunner::class)
class PixelBrowserApplicationBookmarkTest {
    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    @Test
    fun onCreate_bindsRoomBookmarkRepository() {
        assertTrue(app.bookmarkRepository is RoomBookmarkRepository)
    }

    @Test
    fun bindBookmarkRepository_exposesBoundRepository() {
        val repo = InMemoryBookmarkRepository()
        app.bindBookmarkRepository(repo)
        assertTrue(app.bookmarkRepository === repo)
    }

    @Test
    fun suggestionProvider_orders_searchOrGoTo_thenBookmark_thenHistory_dedupingUrl() {
        val repo = InMemoryBookmarkRepository()
        val history = InMemoryHistoryStore()
        app.bindBookmarkRepository(repo)
        app.bindHistoryStore(history, CoroutineScope(Dispatchers.Unconfined))
        runBlocking {
            assertTrue(repo.addBookmark("https://wiki.org/", "Bookmarked Wiki", ROOT_ID) is BookmarkResult.Ok)
            history.insert("https://wiki.org/", "History Wiki", System.currentTimeMillis())
            history.insert("https://wiki-history.org/", "Only History", System.currentTimeMillis())
        }
        val result = runBlocking { app.suggestionProvider.suggest("wiki") }
        val kinds = result.map { it.kind }
        assertTrue("kinds=$kinds", SuggestionKind.Bookmark in kinds && SuggestionKind.History in kinds)
        assertTrue(kinds.indexOf(SuggestionKind.Bookmark) > kinds.indexOf(SuggestionKind.Search))
        assertTrue(kinds.indexOf(SuggestionKind.Bookmark) < kinds.indexOf(SuggestionKind.History))
        assertEquals(1, result.count { it.url == "https://wiki.org/" })
        assertEquals(SuggestionKind.Bookmark, result.first { it.url == "https://wiki.org/" }.kind)
        assertTrue(result.size <= 8)
    }
}
