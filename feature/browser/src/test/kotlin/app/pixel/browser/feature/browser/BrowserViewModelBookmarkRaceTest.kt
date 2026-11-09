package app.pixel.browser.feature.browser

import app.pixel.browser.core.bookmarks.BookmarkRepository
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
import org.junit.Before
import org.junit.Test

/** Spec 013 AC18 – hai lần chạm nhanh không được tạo hai dấu trang cùng URL. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelBookmarkRaceTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Racing(
        val delegate: InMemoryBookmarkRepository = InMemoryBookmarkRepository(),
    ) : BookmarkRepository by delegate {
        var url: String = ""

        /** Trong lúc VM đang chuẩn bị thêm, nơi khác đã lưu cùng URL. */
        override suspend fun lastFolderId(): Long {
            delegate.addBookmark(url, "other", ROOT_ID)
            return delegate.lastFolderId()
        }
    }

    @Test
    fun toggle_whenUrlSavedMeanwhile_doesNotAddDuplicate_andRequestsEdit() {
        val repo = Racing()
        val page = "https://a.com/"
        repo.url = page
        val f = BrowserFixture.onPage(page, bookmarks = repo)
        val edits = mutableListOf<Long>()
        CoroutineScope(Dispatchers.Unconfined).launch { f.viewModel.bookmarkEditRequests.collect { edits += it } }
        f.viewModel.onToggleBookmark()
        val children = runBlocking { repo.delegate.observeChildren(ROOT_ID).first() }
        assertEquals(1, children.size)
        assertEquals(listOf(children.single().id), edits)
    }
}
