package app.pixel.browser.core.bookmarks

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bổ sung sau review spec 013: đếm con, `restore` có kiểm tra, ngày nhập, ưu tiên khớp đầu, giới hạn âm. */
abstract class ReviewFixesContractTest {
    protected abstract fun createRepo(
        maxNodes: Int,
        clock: () -> Long,
    ): BookmarkRepository

    protected open fun release() {}

    private var now = 1_000L
    private val repo: BookmarkRepository by lazy { createRepo(BookmarkLimits.MAX_NODES) { now++ } }

    @After
    fun tearDown() = release()

    private fun <T> BookmarkResult<T>.ok(): T = (this as BookmarkResult.Ok).value

    private fun add(
        url: String,
        title: String,
        parent: Long = ROOT_ID,
    ): Bookmark = runBlocking { repo.addBookmark(url, title, parent).ok() }

    private fun folder(
        title: String,
        parent: Long = ROOT_ID,
    ): BookmarkFolder = runBlocking { repo.createFolder(parent, title).ok() }

    @Test
    fun childCounts_countsDirectChildrenPerParent() =
        runBlocking {
            val f = folder("F")
            add("https://a.com", "a", f.id)
            add("https://b.com", "b", f.id)
            folder("G", f.id)
            add("https://c.com", "c")
            val counts = repo.childCounts()
            assertEquals(3, counts[f.id])
            assertEquals(2, counts[ROOT_ID])
            assertNull(counts[999L])
        }

    private fun deepFolder(levels: Int): Long {
        var parent = ROOT_ID
        repeat(levels) { parent = folder("d$it", parent).id }
        return parent
    }

    @Test
    fun restore_rejectsTreeThatWouldExceedMaxDepth() =
        runBlocking {
            val p16 = deepFolder(BookmarkLimits.MAX_DEPTH)
            val tree =
                DeletedTree(
                    listOf(
                        BookmarkFolder(1_000, p16, "X", 0, 1),
                        BookmarkFolder(1_001, 1_000, "Y", 0, 1),
                    ),
                )
            assertFalse(repo.restore(tree))
            assertTrue(repo.restore(DeletedTree(listOf(BookmarkFolder(1_002, ROOT_ID, "OK", 0, 1)))))
        }

    @Test
    fun restore_acceptsBookmarkInsideDepth16Folder_butNotFolderTreeTooDeep() =
        runBlocking {
            val p15 = deepFolder(BookmarkLimits.MAX_DEPTH - 1)
            val tree =
                DeletedTree(listOf(BookmarkFolder(2_000, p15, "A", 0, 1), BookmarkFolder(2_001, 2_000, "B", 0, 1)))
            assertFalse(repo.restore(tree))
            assertTrue(repo.restore(DeletedTree(listOf(BookmarkFolder(2_002, p15, "A", 0, 1)))))
        }

    @Test
    fun restore_rejectsInvalidUrl() =
        runBlocking {
            val tree = DeletedTree(listOf(Bookmark(3_000, ROOT_ID, "x", "javascript:alert(1)", 0, 1, 1)))
            assertFalse(repo.restore(tree))
            assertTrue(repo.observeChildren(ROOT_ID).first().isEmpty())
        }

    @Test
    fun restore_rejectsNodeWhoseParentIsOutsideTree() =
        runBlocking {
            val existing = folder("E")
            val tree =
                DeletedTree(
                    listOf(
                        BookmarkFolder(4_000, ROOT_ID, "F", 0, 1),
                        Bookmark(4_001, existing.id, "x", "https://a.com/", 0, 1, 1),
                    ),
                )
            assertFalse(repo.restore(tree))
        }

    @Test
    fun restore_acceptsWellFormedTree() =
        runBlocking {
            val tree =
                DeletedTree(
                    listOf(
                        BookmarkFolder(5_000, ROOT_ID, "F", 0, 1),
                        Bookmark(5_001, 5_000, "x", "https://a.com/", 0, 1, 1),
                    ),
                )
            assertTrue(repo.restore(tree))
        }

    @Test
    fun import_keepsValidAddDate_asCreatedAt_soExportRoundTripsIt() =
        runBlocking {
            val tree =
                ImportedFolder(
                    "",
                    emptyList(),
                    listOf(
                        ImportedBookmark("Dated", "https://dated.com/", 1_700_000_000L),
                        ImportedBookmark("Undated", "https://undated.com/", null),
                    ),
                )
            repo.importTree(tree, "w").ok()
            val wrapper = repo.exportTree().folders.single()
            assertEquals(1_700_000_000L, wrapper.bookmarks.first { it.title == "Dated" }.addedAtSeconds)
            assertTrue(wrapper.bookmarks.first { it.title == "Undated" }.addedAtSeconds!! < 1_000_000L)
        }

    @Test
    fun suggest_prefersTitlePrefix_overNewerContainsMatches() =
        runBlocking {
            add("https://old.example/", "wiki home")
            repeat(150) { add("https://site$it.example/", "x wiki $it") }
            val titles = repo.suggest(listOf("wiki"), 3).map { it.title }
            assertTrue("titles=$titles", "wiki home" in titles)
            assertEquals(3, titles.size)
        }

    @Test
    fun negativeLimit_returnsEmpty() =
        runBlocking {
            add("https://a.com/", "a")
            assertTrue(repo.search("a", -1).isEmpty())
            assertTrue(repo.suggest(listOf("a"), -1).isEmpty())
        }

    @Test
    fun titles_dropFormatCharacters() =
        runBlocking {
            val b = add("https://a.com/", "a‮b​c")
            assertEquals("abc", b.title)
            assertEquals("fold", folder("fo⁦ld").title)
        }
}
