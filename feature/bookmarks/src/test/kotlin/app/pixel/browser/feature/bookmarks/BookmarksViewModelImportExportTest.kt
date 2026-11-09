package app.pixel.browser.feature.bookmarks

import app.pixel.browser.core.bookmarks.BookmarkFolder
import app.pixel.browser.core.bookmarks.BookmarkLimits
import app.pixel.browser.core.bookmarks.BookmarkResult
import app.pixel.browser.core.bookmarks.NetscapeBookmarks
import app.pixel.browser.core.bookmarks.ROOT_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** Spec 013 AC16, AC17 – nhập/xuất qua `BookmarkFileGateway` giả (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BookmarksViewModelImportExportTest {
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

    private fun TestScope.newVm(): BookmarksViewModel {
        val vm = BookmarksViewModel(repo, files, { now }, ZoneId.of("UTC"), UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    private fun html(vararg anchors: String) =
        "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>${anchors.joinToString("")}</DL><p>"

    private fun a(
        url: String,
        title: String = "t",
    ) = "<DT><A HREF=\"$url\">$title</A>"

    private fun bytes(s: String) = FileRead.Bytes(s.toByteArray())

    // ---------- AC16 ----------

    @Test
    fun ac16_import_readsAtMostMaxPlusOne_importsOnceUnderRoot_withDatedFolderName() =
        runTest(dispatcher) {
            files.reads = mapOf("u" to bytes(html(a("https://a.com/", "A"), a("https://b.com/", "B"))))
            val vm = newVm()
            vm.import("u")
            advanceUntilIdle()
            assertEquals(listOf("u" to BookmarkLimits.MAX_IMPORT_BYTES + 1), files.readCalls)
            assertEquals(1, repo.importCalls.size)
            assertEquals("Đã nhập 2026-11-10", repo.importCalls.single().second)
            val wrapper = repo.observeChildren(ROOT_ID).first().single() as BookmarkFolder
            assertEquals("Đã nhập 2026-11-10", wrapper.title)
            assertEquals(listOf("A", "B"), repo.observeChildren(wrapper.id).first().map { it.title })
            assertEquals(BookmarksMessage.ImportDone(imported = 2, skipped = 0), vm.state.value.message)
        }

    @Test
    fun ac16_import_countsSkippedAnchors() =
        runTest(dispatcher) {
            files.reads = mapOf("u" to bytes(html(a("https://a.com/"), a("javascript:alert(1)"), a("ftp://x/"))))
            val vm = newVm()
            vm.import("u")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ImportDone(1, 2), vm.state.value.message)
        }

    @Test
    fun ac16_tooLarge_unreadable_notBookmarkFile_reportErrors_andNeverImport() =
        runTest(dispatcher) {
            files.reads =
                mapOf(
                    "big" to FileRead.TooLarge,
                    "bad" to FileRead.Unreadable,
                    "txt" to bytes("hello world, not bookmarks"),
                )
            val vm = newVm()
            vm.import("big")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ImportTooLarge, vm.state.value.message)
            vm.onMessageShown()
            vm.import("bad")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ImportUnreadable, vm.state.value.message)
            vm.onMessageShown()
            vm.import("txt")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ImportNotBookmarkFile, vm.state.value.message)
            assertTrue(repo.importCalls.isEmpty())
            assertTrue(repo.observeChildren(ROOT_ID).first().isEmpty())
        }

    @Test
    fun ac16_oversizedBytesFromGateway_areRejectedByParser_asTooLarge() =
        runTest(dispatcher) {
            files.reads = mapOf("u" to FileRead.Bytes(ByteArray(BookmarkLimits.MAX_IMPORT_BYTES + 1)))
            val vm = newVm()
            vm.import("u")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ImportTooLarge, vm.state.value.message)
            assertTrue(repo.importCalls.isEmpty())
        }

    @Test
    fun ac16_cancel_doesNotReadOrReport() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.import(null)
            advanceUntilIdle()
            assertTrue(files.readCalls.isEmpty())
            assertNull(vm.state.value.message)
        }

    @Test
    fun ac16_importFull_reportsImportFull_andChangesNothing() =
        runTest(dispatcher) {
            files.reads = mapOf("u" to bytes(html(a("https://a.com/"))))
            repo.importResult = BookmarkResult.Full
            val vm = newVm()
            vm.import("u")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ImportFull, vm.state.value.message)
            assertTrue(repo.observeChildren(ROOT_ID).first().isEmpty())
        }

    @Test
    fun ac16_repositoryThrows_isReported_notCrash_noFolderAdded() =
        runTest(dispatcher) {
            files.reads = mapOf("u" to bytes(html(a("https://a.com/"))))
            repo.importFailure = IOException("disk")
            val vm = newVm()
            vm.import("u")
            advanceUntilIdle()
            val m = vm.state.value.message
            assertNotNull(m)
            assertTrue("was $m", m !is BookmarksMessage.ImportDone)
            assertTrue(repo.observeChildren(ROOT_ID).first().isEmpty())
        }

    @Test
    fun ac16_importingSameFileTwice_createsTwoSeparateFolders() =
        runTest(dispatcher) {
            files.reads = mapOf("u" to bytes(html(a("https://a.com/"))))
            val vm = newVm()
            vm.import("u")
            advanceUntilIdle()
            vm.import("u")
            advanceUntilIdle()
            assertEquals(2, repo.observeChildren(ROOT_ID).first().size)
            assertEquals(2, repo.search("a.com", 10).size)
        }

    // ---------- AC17 ----------

    @Test
    fun ac17_export_writesNetscapeOfExportTree_once_andReportsCount() =
        runTest(dispatcher) {
            val f = repo.addFolder("Folder")
            repo.addBm("https://a.com/", "A", f.id)
            repo.addBm("https://b.com/", "B")
            val vm = newVm()
            vm.export("content://out")
            advanceUntilIdle()
            assertEquals(1, files.writes.size)
            assertEquals("content://out", files.writes.single().first)
            assertArrayEquals(NetscapeBookmarks.write(repo.exportTree()), files.writes.single().second)
            assertEquals(BookmarksMessage.ExportDone(2), vm.state.value.message)
        }

    @Test
    fun ac17_exportedTree_hasRootTitleAndDisplayOrder() =
        runTest(dispatcher) {
            repo.addBm("https://b.com/", "BmkB")
            repo.addFolder("FolderF")
            val text =
                run {
                    val vm = newVm()
                    vm.export("u")
                    advanceUntilIdle()
                    files.writes
                        .single()
                        .second
                        .toString(Charsets.UTF_8)
                }
            assertTrue(text.contains("Dấu trang"))
            assertTrue(text.indexOf("FolderF") in 0 until text.indexOf("BmkB"))
        }

    @Test
    fun ac17_writeFailure_reportsExportFailed() =
        runTest(dispatcher) {
            files.writeResult = false
            val vm = newVm()
            vm.export("u")
            advanceUntilIdle()
            assertEquals(BookmarksMessage.ExportFailed, vm.state.value.message)
        }

    @Test
    fun ac17_cancel_doesNotWrite() =
        runTest(dispatcher) {
            val vm = newVm()
            vm.export(null)
            advanceUntilIdle()
            assertTrue(files.writes.isEmpty())
            assertNull(vm.state.value.message)
        }

    @Test
    fun ac17_suggestedFileName_usesClockDate() =
        runTest(dispatcher) {
            val vm = newVm()
            advanceUntilIdle()
            assertEquals("pixel-bookmarks-20261110.html", vm.state.value.exportFileName)
        }
}
