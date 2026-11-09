package app.pixel.browser.feature.bookmarks

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.BookmarkRepository
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.FolderOption
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.ZoneId

/** Bổ sung sau review spec 013: tên gốc/tiền tố nhập bản địa hóa, lỗi repo không làm sập, đếm con một truy vấn. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelReviewTest {
    private val dispatcher = StandardTestDispatcher()
    private val files = FakeBookmarkFileGateway()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Repo bọc cho phép làm hỏng từng thao tác và đếm lời gọi. */
    private class Flaky(
        val delegate: InMemoryBookmarkRepository = InMemoryBookmarkRepository(),
    ) : BookmarkRepository by delegate {
        var failFolder = false
        var failUpdate = false
        var failOptions = false
        val observed = mutableListOf<Long>()
        var countCalls = 0
        var lastFolderSet: Long? = null

        override fun observeChildren(folderId: Long) = delegate.observeChildren(folderId).also { observed += folderId }

        override suspend fun folder(id: Long): BookmarkFolder? {
            if (failFolder) throw IOException("folder")
            return delegate.folder(id)
        }

        override suspend fun updateBookmark(
            id: Long,
            title: String,
            url: String,
            parentId: Long,
        ): BookmarkResult<Bookmark> {
            if (failUpdate) throw IOException("update")
            return delegate.updateBookmark(id, title, url, parentId)
        }

        override suspend fun folderOptions(excludeSubtreeOf: Long?): List<FolderOption> {
            if (failOptions) throw IOException("options")
            return delegate.folderOptions(excludeSubtreeOf)
        }

        override suspend fun childCounts(): Map<Long, Int> {
            countCalls++
            return delegate.childCounts()
        }

        override suspend fun setLastFolderId(id: Long) {
            lastFolderSet = id
            delegate.setLastFolderId(id)
        }
    }

    private fun TestScope.vm(
        repo: BookmarkRepository,
        rootTitle: String = "Dấu trang",
        prefix: String = "Đã nhập",
    ): BookmarksViewModel {
        val vm =
            BookmarksViewModel(
                repo,
                files,
                { 0L },
                ZoneId.of("UTC"),
                UnconfinedTestDispatcher(testScheduler),
                rootTitle = rootTitle,
                importFolderPrefix = prefix,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    @Test
    fun rootTitle_isLocalized_inScreenTitle_options_hits_andExport() =
        runTest(dispatcher) {
            val repo = Flaky()
            repo.delegate.addBm("https://wiki.org/", "Wiki")
            val vm = vm(repo, rootTitle = "Bookmarks")
            advanceUntilIdle()
            assertEquals("Bookmarks", vm.state.value.title)
            assertEquals("Bookmarks", vm.folderOptions(null).first { it.id == ROOT_ID }.title)
            vm.onQueryChange("wiki")
            advanceTimeBy(400)
            advanceUntilIdle()
            assertEquals(
                "Bookmarks",
                vm.state.value.results!!
                    .single()
                    .folderTitle,
            )
            vm.export("u")
            advanceUntilIdle()
            val text =
                files.writes
                    .single()
                    .second
                    .toString(Charsets.UTF_8)
            assertTrue(text.contains("<H1>Bookmarks</H1>"))
        }

    @Test
    fun importFolderPrefix_isConfigurable() =
        runTest(dispatcher) {
            val repo = Flaky()
            files.reads = mapOf("u" to FileRead.Bytes("<DL><DT><A HREF=\"https://a.com/\">A</A></DL>".toByteArray()))
            val vm = vm(repo, prefix = "Imported")
            vm.import("u")
            advanceUntilIdle()
            val wrapper =
                repo.delegate
                    .exportTree()
                    .folders
                    .single()
            assertEquals("Imported 1970-01-01", wrapper.title)
        }

    @Test
    fun search_whenFolderLookupFails_doesNotCrash() =
        runTest(dispatcher) {
            val repo = Flaky()
            repo.delegate.addBm("https://wiki.org/", "Wiki")
            val vm = vm(repo)
            advanceUntilIdle()
            repo.failFolder = true
            vm.onQueryChange("wiki")
            advanceTimeBy(400)
            advanceUntilIdle()
            assertEquals(
                1,
                vm.state.value.results
                    ?.size,
            )
        }

    @Test
    fun edit_andFolderOptions_swallowRepositoryErrors() =
        runTest(dispatcher) {
            val repo = Flaky()
            val b = repo.delegate.addBm("https://a.com/", "A")
            val vm = vm(repo)
            repo.failUpdate = true
            repo.failOptions = true
            assertEquals(BookmarkResult.NotFound, vm.edit(b.id, "x", "https://b.com/", ROOT_ID))
            assertTrue(vm.folderOptions(null).isEmpty())
        }

    @Test
    fun edit_ok_remembersChosenFolder() =
        runTest(dispatcher) {
            val repo = Flaky()
            val f = repo.delegate.addFolder("F")
            val b = repo.delegate.addBm("https://a.com/", "A")
            val vm = vm(repo)
            assertTrue(vm.edit(b.id, "A", "https://a.com/", f.id) is BookmarkResult.Ok)
            assertEquals(f.id, repo.lastFolderSet)
        }

    @Test
    fun childCounts_comeFromOneQuery_notOneObservePerFolder() =
        runTest(dispatcher) {
            val repo = Flaky()
            val f1 = repo.delegate.addFolder("F1")
            val f2 = repo.delegate.addFolder("F2")
            repo.delegate.addBm("https://a.com/", "A", f1.id)
            val vm = vm(repo)
            advanceUntilIdle()
            assertEquals(1, vm.state.value.childCounts[f1.id])
            assertTrue(repo.countCalls >= 1)
            assertTrue("observed=${repo.observed}", f2.id !in repo.observed && f1.id !in repo.observed)
        }
}

/** Rời màn hình khi snackbar hoàn tác đang hiện không được để `pendingUndo` treo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BookmarksRouteDisposeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun leavingScreen_clearsPendingUndo() {
        val repo = InMemoryBookmarkRepository()
        val b = repo.addBm("https://a.com/", "A")
        val vm = BookmarksViewModel(repo, FakeBookmarkFileGateway(), { 0L }, ZoneId.of("UTC"), Dispatchers.Unconfined)
        var shown by mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                if (shown) BookmarksRoute(vm, onOpenUrl = {}, onOpenInNewTab = {}, onClose = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.item(b.id)).performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.MENU_DELETE).performClick()
        rule.waitForIdle()
        assertNotNull(vm.state.value.pendingUndo)
        shown = false
        rule.waitForIdle()
        assertNull(vm.state.value.pendingUndo)
    }
}
