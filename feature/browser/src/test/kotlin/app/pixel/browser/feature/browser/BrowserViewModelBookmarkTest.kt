package app.pixel.browser.feature.browser

import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Spec 013 AC18 – ngôi sao và thêm nhanh dấu trang trong `BrowserViewModel` (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelBookmarkTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class SpyRepo(
        val delegate: InMemoryBookmarkRepository = InMemoryBookmarkRepository(),
    ) : BookmarkRepository by delegate {
        val addCalls = mutableListOf<Triple<String, String, Long>>()
        var setLastCalls = 0
        var deleteCalls = 0

        override suspend fun addBookmark(
            url: String,
            title: String,
            parentId: Long,
        ): BookmarkResult<Bookmark> {
            addCalls += Triple(url, title, parentId)
            return delegate.addBookmark(url, title, parentId)
        }

        override suspend fun setLastFolderId(id: Long) {
            setLastCalls++
            delegate.setLastFolderId(id)
        }

        override suspend fun delete(id: Long): BookmarkResult<app.pixel.browser.core.bookmarks.DeletedTree> {
            deleteCalls++
            return delegate.delete(id)
        }
    }

    private val a = "https://a.com/"
    private val b = "https://b.com/page"
    private val repo = SpyRepo()

    private fun onPage(url: String = a) = BrowserFixture.onPage(url, bookmarks = repo)

    private fun BrowserFixture.openOtherPage(url: String) {
        selected().visit(url)
    }

    private fun edits(f: BrowserFixture): MutableList<Long> {
        val got = mutableListOf<Long>()
        CoroutineScope(Dispatchers.Unconfined).launch { f.viewModel.bookmarkEditRequests.collect { got += it } }
        return got
    }

    @Test
    fun ac18_unsavedWebPage_canBookmark_notBookmarked() {
        val s = onPage().state()
        assertTrue(s.canBookmark)
        assertFalse(s.isBookmarked)
        assertNull(s.bookmarkId)
    }

    @Test
    fun ac18_toggle_addsOnce_withTitleAndLastFolder_marksStar_andMessages() {
        val folder = runBlocking { (repo.createFolder(ROOT_ID, "F") as BookmarkResult.Ok).value }
        runBlocking { repo.setLastFolderId(folder.id) }
        repo.setLastCalls = 0
        val f = onPage()
        f.selected().fake.simulateTitle("Trang A")
        f.viewModel.onToggleBookmark()
        assertEquals(listOf(Triple(a, "Trang A", folder.id)), repo.addCalls)
        val s = f.state()
        assertTrue(s.isBookmarked)
        val stored = runBlocking { repo.observeByUrl(a).first() }
        assertEquals(stored?.id, s.bookmarkId)
        assertEquals(BrowserMessages.BOOKMARK_ADDED, s.message)
        assertEquals(0, repo.setLastCalls)
    }

    @Test
    fun ac18_toggle_defaultsToRootFolder() {
        val f = onPage()
        f.viewModel.onToggleBookmark()
        assertEquals(ROOT_ID, repo.addCalls.single().third)
    }

    @Test
    fun ac18_toggleAgain_whenSaved_neitherAddsNorRemoves_butRequestsEdit() {
        val f = onPage()
        val edits = edits(f)
        f.viewModel.onToggleBookmark()
        val id = f.state().bookmarkId!!
        f.viewModel.onToggleBookmark()
        assertEquals(1, repo.addCalls.size)
        assertEquals(0, repo.deleteCalls)
        assertEquals(listOf(id), edits)
        assertTrue(f.state().isBookmarked)
    }

    @Test
    fun ac18_navigating_updatesStarByUrlKey_ignoringFragmentAndHostCase() {
        runBlocking { repo.addBookmark(b, "B", ROOT_ID) }
        val f = onPage()
        assertFalse(f.state().isBookmarked)
        f.openOtherPage("https://B.com/page#frag")
        assertTrue(f.state().isBookmarked)
        f.openOtherPage("https://b.com/other")
        assertFalse(f.state().isBookmarked)
    }

    @Test
    fun ac18_switchingTab_updatesStar() {
        runBlocking { repo.addBookmark(b, "B", ROOT_ID) }
        val f = onPage()
        f.viewModel.onOpenUrlInNewTab(b)
        assertTrue(f.state().isBookmarked)
        f.viewModel.onSelectTab(BrowserFixture.PAGE_TAB_ID)
        assertFalse(f.state().isBookmarked)
    }

    @Test
    fun ac18_bookmarkDeletedElsewhere_turnsStarOff() {
        val f = onPage()
        f.viewModel.onToggleBookmark()
        val id = f.state().bookmarkId!!
        runBlocking { repo.delete(id) }
        val s = f.state()
        assertFalse(s.isBookmarked)
        assertNull(s.bookmarkId)
    }

    @Test
    fun ac18_atHome_cannotBookmark_toggleDoesNothing() {
        val f = BrowserFixture(bookmarks = repo)
        assertTrue(f.state().isHome)
        assertFalse(f.state().canBookmark)
        f.viewModel.onToggleBookmark()
        assertTrue(repo.addCalls.isEmpty())
        assertNull(f.state().message)
    }

    @Test
    fun ac18_aboutBlank_cannotBookmark() {
        val f = BrowserFixture.onPage("about:blank", bookmarks = repo)
        assertFalse(f.state().canBookmark)
        f.viewModel.onToggleBookmark()
        assertTrue(repo.addCalls.isEmpty())
    }

    @Test
    fun ac18_withoutRepository_cannotBookmark_toggleIsNoOp() {
        val f = BrowserFixture.onPage(a, bookmarks = null)
        assertFalse(f.state().canBookmark)
        f.viewModel.onToggleBookmark()
        assertFalse(f.state().isBookmarked)
        assertNull(f.state().message)
    }

    @Test
    fun ac18_addFull_reportsFailure_noCrash_noStar() {
        val full = InMemoryBookmarkRepository(maxNodes = 0)
        val f = BrowserFixture.onPage(a, bookmarks = full)
        f.viewModel.onToggleBookmark()
        assertEquals(BrowserMessages.BOOKMARK_FAILED, f.state().message)
        assertFalse(f.state().isBookmarked)
    }

    @Test
    fun ac18_repositoryThrows_reportsFailure_noCrash() {
        val broken = InMemoryBookmarkRepository().apply { failWith = IOException("disk") }
        val f = BrowserFixture.onPage(a, bookmarks = broken)
        f.viewModel.onToggleBookmark()
        assertEquals(BrowserMessages.BOOKMARK_FAILED, f.state().message)
    }

    @Test
    fun ac18_privateTab_canStillBookmark() {
        val f = onPage()
        f.tabManager.openTab(b, select = true, isPrivate = true)
        assertTrue(f.state().canBookmark)
        f.viewModel.onToggleBookmark()
        assertEquals(b, repo.addCalls.single().first)
        assertTrue(f.state().isBookmarked)
    }
}
