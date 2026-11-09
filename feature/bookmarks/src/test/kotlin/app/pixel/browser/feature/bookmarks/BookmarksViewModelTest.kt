package app.pixel.browser.feature.bookmarks

import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Spec 013 AC21 (logic), AC23 (logic), AC24, AC25 (logic) – `BookmarksViewModel` (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = SpyBookmarkRepository()
    private val files = FakeBookmarkFileGateway()
    private val now = Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newVm(r: app.pixel.browser.core.bookmarks.BookmarkRepository = repo): BookmarksViewModel {
        val vm = BookmarksViewModel(r, files, { now }, ZoneId.of("UTC"), UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    private val BookmarksViewModel.s get() = state.value

    // ---------- AC21 ----------

    @Test
    fun ac21_startsAtRoot_titleIsDauTrang_navigateUpAtRootReturnsFalse() =
        runTest(dispatcher) {
            val vm = newVm()
            advanceUntilIdle()
            assertEquals(ROOT_ID, vm.s.folderId)
            assertEquals("Dấu trang", vm.s.title)
            assertFalse(vm.navigateUp())
            assertEquals(ROOT_ID, vm.s.folderId)
        }

    @Test
    fun ac21_openFolder_showsItsNameAndChildren_navigateUpGoesToParent() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            val g = repo.addFolder("G", f.id)
            repo.addBm("https://x.com", "X", g.id)
            val vm = newVm()
            vm.openFolder(f.id)
            advanceUntilIdle()
            assertEquals(f.id, vm.s.folderId)
            assertEquals("F", vm.s.title)
            assertEquals(listOf("G"), vm.s.children.map { it.title })
            vm.openFolder(g.id)
            advanceUntilIdle()
            assertEquals("G", vm.s.title)
            assertTrue(vm.navigateUp())
            advanceUntilIdle()
            assertEquals(f.id, vm.s.folderId)
            assertTrue(vm.navigateUp())
            advanceUntilIdle()
            assertEquals(ROOT_ID, vm.s.folderId)
            assertFalse(vm.navigateUp())
        }

    @Test
    fun ac21_currentFolderDeletedElsewhere_fallsBackToNearestExistingAncestor() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            val g = repo.addFolder("G", f.id)
            val h = repo.addFolder("H", g.id)
            val vm = newVm()
            vm.openFolder(h.id)
            advanceUntilIdle()
            repo.delete(g.id)
            advanceUntilIdle()
            assertEquals(f.id, vm.s.folderId)
            assertEquals("F", vm.s.title)
        }

    @Test
    fun ac22_folderRows_carryChildCounts() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            repo.addBm("https://a.com", "a", f.id)
            repo.addBm("https://b.com", "b", f.id)
            repo.addFolder("Sub", f.id)
            val vm = newVm()
            advanceUntilIdle()
            assertEquals(3, vm.s.childCounts[f.id])
        }

    // ---------- AC23 ----------

    @Test
    fun ac23_query_isDebouncedBy300ms_andOnlyLastValueIsSearched() =
        runTest(dispatcher) {
            repo.addBm("https://wiki.org/", "Wikipedia")
            val vm = newVm()
            advanceUntilIdle()
            vm.onQueryChange("w")
            advanceTimeBy(100)
            vm.onQueryChange("wi")
            advanceTimeBy(100)
            vm.onQueryChange("wik")
            advanceTimeBy(299)
            assertNull("not yet", vm.s.results)
            advanceTimeBy(2)
            advanceUntilIdle()
            assertEquals(listOf("wik"), repo.searchCalls)
            assertEquals(listOf("Wikipedia"), vm.s.results?.map { it.bookmark.title })
        }

    @Test
    fun ac23_results_areFlatAcrossTree_withParentFolderTitle() =
        runTest(dispatcher) {
            val f = repo.addFolder("Reading")
            repo.addBm("https://wiki.org/", "Wikipedia", f.id)
            repo.addBm("https://wikia.com/", "Wikia")
            val vm = newVm()
            vm.onQueryChange("wik")
            advanceTimeBy(400)
            advanceUntilIdle()
            val byTitle = vm.s.results!!.associate { it.bookmark.title to it.folderTitle }
            assertEquals(mapOf("Wikia" to "Dấu trang", "Wikipedia" to "Reading"), byTitle)
        }

    @Test
    fun ac23_clearingQuery_returnsToFolderList_immediately() =
        runTest(dispatcher) {
            repo.addBm("https://wiki.org/", "Wikipedia")
            val vm = newVm()
            vm.onQueryChange("wiki")
            advanceTimeBy(400)
            advanceUntilIdle()
            assertNotNull(vm.s.results)
            vm.onQueryChange("")
            advanceUntilIdle()
            assertNull(vm.s.results)
            assertEquals("", vm.s.query)
        }

    // ---------- AC24 ----------

    @Test
    fun ac24_createFolder_inCurrentFolder() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            val vm = newVm()
            vm.openFolder(f.id)
            advanceUntilIdle()
            vm.createFolder("Sub")
            advanceUntilIdle()
            assertEquals(listOf("Sub"), repo.observeChildren(f.id).first().map { it.title })
            assertEquals(listOf("Sub"), vm.s.children.map { it.title })
        }

    @Test
    fun ac24_createFolder_blankTitle_reportsFailure_noFolderCreated() =
        runTest(dispatcher) {
            val vm = newVm()
            advanceUntilIdle()
            vm.createFolder("   ")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.OperationFailed, vm.s.message)
            assertTrue(repo.observeChildren(ROOT_ID).first().isEmpty())
        }

    @Test
    fun ac24_rename_changesFolderTitle_blankIsRejected() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            val vm = newVm()
            advanceUntilIdle()
            vm.rename(f.id, "Báo")
            advanceUntilIdle()
            assertEquals("Báo", (repo.observeChildren(ROOT_ID).first().single() as BookmarkFolder).title)
            vm.rename(f.id, " ")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.OperationFailed, vm.s.message)
            assertEquals(
                "Báo",
                repo
                    .observeChildren(ROOT_ID)
                    .first()
                    .single()
                    .title,
            )
        }

    @Test
    fun ac24_move_movesToTargetFolder() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            val b = repo.addBm("https://a.com", "A")
            val vm = newVm()
            advanceUntilIdle()
            vm.move(b.id, f.id)
            advanceUntilIdle()
            assertEquals(listOf("A"), repo.observeChildren(f.id).first().map { it.title })
        }

    @Test
    fun ac24_move_cycleFromRepository_becomesErrorMessage_notCrash() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            repo.moveResult = BookmarkResult.Cycle
            val vm = newVm()
            advanceUntilIdle()
            vm.move(f.id, f.id)
            advanceUntilIdle()
            assertEquals(BookmarksMessage.OperationFailed, vm.s.message)
        }

    @Test
    fun ac24_moveOptions_excludeItemAndDescendants() =
        runTest(dispatcher) {
            val a = repo.addFolder("A")
            repo.addFolder("A1", a.id)
            val b = repo.addFolder("B")
            val vm = newVm()
            val ids = vm.folderOptions(excludeSubtreeOf = a.id).map { it.id }
            assertEquals(listOf(ROOT_ID, b.id), ids)
        }

    @Test
    fun ac24_edit_updatesBookmark_andReturnsRepositoryResult() =
        runTest(dispatcher) {
            val b = repo.addBm("https://a.com", "A")
            val vm = newVm()
            val ok = vm.edit(b.id, "New", "https://b.com/", ROOT_ID)
            assertTrue(ok is BookmarkResult.Ok)
            assertEquals("New", (ok as BookmarkResult.Ok<Bookmark>).value.title)
            assertEquals(BookmarkResult.InvalidUrl, vm.edit(b.id, "New", "nope", ROOT_ID))
        }

    @Test
    fun ac24_onMessageShown_clearsMessage() =
        runTest(dispatcher) {
            val vm = newVm()
            advanceUntilIdle()
            vm.createFolder("")
            advanceUntilIdle()
            assertNotNull(vm.s.message)
            vm.onMessageShown()
            advanceUntilIdle()
            assertNull(vm.s.message)
        }

    // ---------- AC25 ----------

    @Test
    fun ac25_delete_removesAtOnce_andHoldsUndo_undoRestoresSamePlace() =
        runTest(dispatcher) {
            val f = repo.addFolder("F")
            val g = repo.addFolder("G")
            repo.addBm("https://x.com", "X", f.id)
            val vm = newVm()
            advanceUntilIdle()
            val before = repo.observeChildren(ROOT_ID).first()
            vm.delete(f.id)
            advanceUntilIdle()
            assertEquals(listOf(g.id), vm.s.children.map { it.id })
            assertNotNull(vm.s.pendingUndo)
            vm.undoDelete()
            advanceUntilIdle()
            assertEquals(before, vm.s.children)
            assertEquals(1, repo.observeChildren(f.id).first().size)
            assertNull(vm.s.pendingUndo)
            assertEquals(1, repo.restoreCalls.size)
        }

    @Test
    fun ac25_secondDelete_dropsFirstUndo_onlyNewestIsRestorable() =
        runTest(dispatcher) {
            val a = repo.addBm("https://a.com", "A")
            val b = repo.addBm("https://b.com", "B")
            val vm = newVm()
            advanceUntilIdle()
            vm.delete(a.id)
            advanceUntilIdle()
            vm.delete(b.id)
            advanceUntilIdle()
            vm.undoDelete()
            advanceUntilIdle()
            assertEquals(listOf("B"), repo.observeChildren(ROOT_ID).first().map { it.title })
            vm.undoDelete()
            advanceUntilIdle()
            assertEquals(listOf("B"), repo.observeChildren(ROOT_ID).first().map { it.title })
        }

    @Test
    fun ac25_undoExpired_dropsPendingUndo_andUndoBecomesNoOp() =
        runTest(dispatcher) {
            val a = repo.addBm("https://a.com", "A")
            val vm = newVm()
            advanceUntilIdle()
            vm.delete(a.id)
            advanceUntilIdle()
            vm.onUndoExpired()
            advanceUntilIdle()
            assertNull(vm.s.pendingUndo)
            vm.undoDelete()
            advanceUntilIdle()
            assertTrue(repo.observeChildren(ROOT_ID).first().isEmpty())
            assertTrue(repo.restoreCalls.isEmpty())
        }

    @Test
    fun ac25_deletingRoot_isRefused_noUndoOffered() =
        runTest(dispatcher) {
            val vm = newVm()
            advanceUntilIdle()
            vm.delete(ROOT_ID)
            advanceUntilIdle()
            assertNull(vm.s.pendingUndo)
        }
}
