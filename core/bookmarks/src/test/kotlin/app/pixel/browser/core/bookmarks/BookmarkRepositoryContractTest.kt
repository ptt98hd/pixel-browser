package app.pixel.browser.core.bookmarks

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 013 AC2-AC9 – hợp đồng chung cho mọi [BookmarkRepository]; chạy trên InMemory (JVM) và Room (Robolectric).
 * Giả định (spec chưa nói rõ): `maxNodes` không tính gốc hệ thống.
 */
abstract class BookmarkRepositoryContractTest {
    protected abstract fun createRepo(
        maxNodes: Int,
        clock: () -> Long,
    ): BookmarkRepository

    protected open fun release() {}

    private var now = 1_000L
    private val tick: () -> Long = { now++ }
    private var maxNodes = BookmarkLimits.MAX_NODES
    private val repo: BookmarkRepository by lazy { createRepo(maxNodes, tick) }

    @After
    fun tearDown() = release()

    private fun <T> run(block: suspend () -> T): T = runBlocking { withTimeout(20_000) { block() } }

    private fun <T> BookmarkResult<T>.ok(): T {
        assertTrue("expected Ok but was $this", this is BookmarkResult.Ok)
        return (this as BookmarkResult.Ok).value
    }

    private fun add(
        url: String,
        title: String = "",
        parent: Long = ROOT_ID,
    ): Bookmark = run { repo.addBookmark(url, title, parent).ok() }

    private fun folder(
        title: String,
        parent: Long = ROOT_ID,
    ): BookmarkFolder = run { repo.createFolder(parent, title).ok() }

    private fun children(id: Long = ROOT_ID): List<BookmarkNode> = run { repo.observeChildren(id).first() }

    private fun titles(id: Long = ROOT_ID) = children(id).map { it.title }

    /** Gom các giá trị flow phát ra để kiểm "phát lại sau mỗi thay đổi". */
    private class Collector<T>(
        flow: Flow<T>,
    ) {
        private val channel = Channel<T>(Channel.UNLIMITED)
        private val job =
            CoroutineScopeHolder.scope.launch(start = CoroutineStart.DEFAULT) {
                flow.collect { channel.send(it) }
            }

        fun awaitUntil(predicate: (T) -> Boolean): T =
            runBlocking {
                withTimeout(10_000) {
                    var v = channel.receive()
                    while (!predicate(v)) v = channel.receive()
                    v
                }
            }

        fun stop() = job.cancel()
    }

    private object CoroutineScopeHolder {
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default)
    }

    // ---------- AC2 ----------

    @Test
    fun ac2_freshDb_hasOnlyRoot_withDefaultLastFolder() {
        assertTrue(children().isEmpty())
        val root = run { repo.folder(ROOT_ID) }
        assertEquals("Dấu trang", root?.title)
        assertNull(root?.parentId)
        assertEquals(ROOT_ID, run { repo.lastFolderId() })
    }

    @Test
    fun ac2_addBookmark_emptyTitle_usesHost_positionsIncrease_timestampsPositive() {
        val a = add("https://a.com", "")
        val b = add("https://b.com", "B")
        assertEquals("a.com", a.title)
        assertEquals("https://a.com", a.url)
        assertTrue(b.position > a.position)
        assertTrue(a.createdAt > 0 && a.updatedAt > 0)
    }

    @Test
    fun ac2_addBookmark_invalidUrl_isInvalidUrl() {
        assertEquals(BookmarkResult.InvalidUrl, run { repo.addBookmark("javascript:alert(1)", "x", ROOT_ID) })
        assertEquals(BookmarkResult.InvalidUrl, run { repo.addBookmark("example.com", "x", ROOT_ID) })
    }

    @Test
    fun ac2_addBookmark_missingParentOrNonFolderParent_isNotFound() {
        val b = add("https://a.com")
        assertEquals(BookmarkResult.NotFound, run { repo.addBookmark("https://b.com", "b", 9_999L) })
        assertEquals(BookmarkResult.NotFound, run { repo.addBookmark("https://b.com", "b", b.id) })
    }

    @Test
    fun ac2_addBookmark_whenFull_isFull_andNothingAdded() {
        maxNodes = 3
        repeat(3) { add("https://a$it.com") }
        assertEquals(BookmarkResult.Full, run { repo.addBookmark("https://x.com", "x", ROOT_ID) })
        assertEquals(BookmarkResult.Full, run { repo.createFolder(ROOT_ID, "f") })
        assertEquals(3, children().size)
    }

    @Test
    fun ac2_observeChildren_reemitsAfterEachChange_foldersBeforeBookmarks() {
        val c = Collector(repo.observeChildren(ROOT_ID))
        try {
            assertTrue(c.awaitUntil { true }.isEmpty())
            add("https://a.com", "A")
            assertEquals(listOf("A"), c.awaitUntil { it.size == 1 }.map { it.title })
            folder("Z folder")
            val list = c.awaitUntil { it.size == 2 }
            assertTrue(list[0] is BookmarkFolder)
            assertTrue(list[1] is Bookmark)
        } finally {
            c.stop()
        }
    }

    @Test
    fun ac2_order_isFoldersFirst_thenByPositionWithinGroup() {
        add("https://b1.com", "b1")
        folder("f1")
        add("https://b2.com", "b2")
        folder("f2")
        assertEquals(listOf("f1", "f2", "b1", "b2"), titles())
    }

    // ---------- AC3 ----------

    @Test
    fun ac3_createFolder_ok_blankTitleIsInvalid_duplicateNamesAllowed() {
        assertEquals("Tin tức", folder("Tin tức").title)
        assertEquals(BookmarkResult.InvalidTitle, run { repo.createFolder(ROOT_ID, "   ") })
        assertEquals(BookmarkResult.InvalidTitle, run { repo.createFolder(ROOT_ID, "") })
        folder("Tin tức")
        assertEquals(listOf("Tin tức", "Tin tức"), titles())
    }

    @Test
    fun ac3_createFolder_atDepth17_isTooDeep_depth16Allowed() {
        var parent = ROOT_ID
        repeat(BookmarkLimits.MAX_DEPTH) { parent = folder("d${it + 1}", parent).id }
        assertEquals(BookmarkResult.TooDeep, run { repo.createFolder(parent, "d17") })
        // dấu trang vẫn thêm được vào thư mục sâu 16
        add("https://deep.com", "deep", parent)
    }

    @Test
    fun ac3_rename_root_isProtected_folderChangesTitleAndUpdatedAt() {
        assertEquals(BookmarkResult.Protected, run { repo.rename(ROOT_ID, "x") })
        val f = folder("Tin")
        val before = children().single()
        assertEquals(BookmarkResult.Ok(Unit), run { repo.rename(f.id, "Báo") })
        val after = children().single()
        assertEquals("Báo", after.title)
        assertEquals(before.id, after.id)
        assertEquals(BookmarkResult.InvalidTitle, run { repo.rename(f.id, "  ") })
        assertEquals(BookmarkResult.NotFound, run { repo.rename(9_999L, "x") })
    }

    // ---------- AC4 ----------

    @Test
    fun ac4_updateBookmark_changesAllThree_keepsIdAndCreatedAt_bumpsUpdatedAt() {
        val f = folder("F")
        val b = add("https://a.com", "A")
        now += 100
        val u = run { repo.updateBookmark(b.id, "New", "https://b.com/p", f.id).ok() }
        assertEquals(b.id, u.id)
        assertEquals(b.createdAt, u.createdAt)
        assertTrue(u.updatedAt > b.updatedAt)
        assertEquals("New", u.title)
        assertEquals("https://b.com/p", u.url)
        assertEquals(f.id, u.parentId)
        assertEquals(listOf(u), children(f.id))
        assertEquals(listOf("F"), titles())
    }

    @Test
    fun ac4_updateBookmark_invalidUrl_leavesDataUnchanged() {
        val b = add("https://a.com", "A")
        assertEquals(BookmarkResult.InvalidUrl, run { repo.updateBookmark(b.id, "X", "nope", ROOT_ID) })
        assertEquals(listOf<BookmarkNode>(b), children())
    }

    @Test
    fun ac4_updateBookmark_toOtherFolder_goesToBottom() {
        val f = folder("F")
        add("https://x1.com", "x1", f.id)
        val x2 = add("https://x2.com", "x2", f.id)
        val b = add("https://a.com", "A")
        val u = run { repo.updateBookmark(b.id, "A", "https://a.com", f.id).ok() }
        assertTrue(u.position > x2.position)
        assertEquals(listOf("x1", "x2", "A"), titles(f.id))
    }

    @Test
    fun ac4_updateBookmark_unknownId_isNotFound() {
        assertEquals(BookmarkResult.NotFound, run { repo.updateBookmark(9_999L, "x", "https://a.com", ROOT_ID) })
    }

    @Test
    fun ac4_bookmarkById_returnsBookmark_nullForFolderOrMissing() {
        val b = add("https://a.com", "A")
        val f = folder("F")
        assertEquals(b, run { repo.bookmark(b.id) })
        assertNull(run { repo.bookmark(f.id) })
        assertNull(run { repo.bookmark(9_999L) })
    }

    // ---------- AC5 ----------

    @Test
    fun ac5_move_bookmarkToFolder_ok_goesToBottom() {
        val f = folder("F")
        add("https://x.com", "x", f.id)
        val b = add("https://a.com", "A")
        assertEquals(BookmarkResult.Ok(Unit), run { repo.move(b.id, f.id) })
        assertEquals(listOf("x", "A"), titles(f.id))
        assertTrue(titles().none { it == "A" })
    }

    @Test
    fun ac5_move_intoItselfOrDescendant_isCycle_treeUnchanged() {
        val a = folder("A")
        val c = folder("C", a.id)
        val d = folder("D", c.id)
        val before = listOf(children(), children(a.id), children(c.id), children(d.id))
        assertEquals(BookmarkResult.Cycle, run { repo.move(a.id, a.id) })
        assertEquals(BookmarkResult.Cycle, run { repo.move(a.id, d.id) })
        assertEquals(before, listOf(children(), children(a.id), children(c.id), children(d.id)))
    }

    @Test
    fun ac5_move_root_isProtected() {
        val f = folder("F")
        assertEquals(BookmarkResult.Protected, run { repo.move(ROOT_ID, f.id) })
    }

    @Test
    fun ac5_move_makingSubtreeDeeperThan16_isTooDeep_leafFolderAtDepth16Ok() {
        var parent = ROOT_ID
        repeat(15) { parent = folder("d${it + 1}", parent).id }
        val deepest = parent // depth 15
        val b = folder("B") // depth 1
        folder("C", b.id) // depth 2 -> would become 17 under deepest
        val leaf = folder("Leaf") // depth 1 -> depth 16 under deepest, ok
        assertEquals(BookmarkResult.TooDeep, run { repo.move(b.id, deepest) })
        assertTrue(titles().contains("B"))
        assertEquals(BookmarkResult.Ok(Unit), run { repo.move(leaf.id, deepest) })
    }

    @Test
    fun ac5_move_toNonFolderOrMissing_isNotFound() {
        val a = add("https://a.com")
        val b = add("https://b.com")
        assertEquals(BookmarkResult.NotFound, run { repo.move(a.id, b.id) })
        assertEquals(BookmarkResult.NotFound, run { repo.move(a.id, 9_999L) })
        assertEquals(BookmarkResult.NotFound, run { repo.move(9_999L, ROOT_ID) })
    }

    // ---------- AC6 ----------

    private class Tree(
        val f: BookmarkFolder,
        val g: BookmarkFolder,
        val x: Bookmark,
        val y: Bookmark,
    )

    private fun seedTree(): Tree {
        val f = folder("F")
        val g = folder("G", f.id)
        val x = add("https://x.com", "X", f.id)
        val y = add("https://y.com", "Y", g.id)
        return Tree(f, g, x, y)
    }

    @Test
    fun ac6_deleteFolder_removesWholeSubtree_andObserversSeeNull() {
        val t = seedTree()
        val c = Collector(repo.observeByUrl("https://x.com"))
        try {
            c.awaitUntil { it != null }
            val tree = run { repo.delete(t.f.id).ok() }
            assertTrue(tree.nodes.map { it.id }.containsAll(listOf(t.f.id, t.g.id, t.x.id, t.y.id)))
            assertTrue(children().isEmpty())
            assertTrue(children(t.f.id).isEmpty())
            assertNull(run { repo.folder(t.g.id) })
            assertNull(c.awaitUntil { it == null })
            assertNull(run { repo.observeByUrl("https://y.com").first() })
        } finally {
            c.stop()
        }
    }

    @Test
    fun ac6_restore_bringsBackIdenticalTree_secondRestoreIsFalse() {
        folder("Before")
        val t = seedTree()
        folder("After")
        val snapshot = listOf(children(), children(t.f.id), children(t.g.id))
        val tree = run { repo.delete(t.f.id).ok() }
        assertTrue(run { repo.restore(tree) })
        assertEquals(snapshot, listOf(children(), children(t.f.id), children(t.g.id)))
        assertEquals(listOf("Before", "F", "After"), titles())
        assertFalse(run { repo.restore(tree) })
        assertEquals(snapshot, listOf(children(), children(t.f.id), children(t.g.id)))
    }

    @Test
    fun ac6_restore_whenParentOfTreeRootWasDeleted_isFalse() {
        val p = folder("P")
        val x = add("https://x.com", "X", p.id)
        val treeX = run { repo.delete(x.id).ok() }
        run { repo.delete(p.id).ok() }
        assertFalse(run { repo.restore(treeX) })
        assertTrue(children().isEmpty())
    }

    @Test
    fun ac6_deleteRoot_isProtected_deleteMissingIsNotFound() {
        assertEquals(BookmarkResult.Protected, run { repo.delete(ROOT_ID) })
        assertEquals(BookmarkResult.NotFound, run { repo.delete(9_999L) })
    }

    @Test
    fun ac6_deleteSingleBookmark_andRestore_works() {
        val a = add("https://a.com", "A")
        add("https://b.com", "B")
        val tree = run { repo.delete(a.id).ok() }
        assertEquals(listOf("B"), titles())
        assertTrue(run { repo.restore(tree) })
        assertEquals(listOf("A", "B"), titles())
        assertEquals(a, children().first())
    }

    // ---------- AC7 ----------

    @Test
    fun ac7_observeByUrl_nullThenBookmarkThenNull_matchingByUrlKey() {
        val c = Collector(repo.observeByUrl("https://a.com/"))
        try {
            assertNull(c.awaitUntil { true })
            val b = add("https://A.com#frag", "A")
            assertEquals(b.id, c.awaitUntil { it != null }?.id)
            run { repo.delete(b.id).ok() }
            assertNull(c.awaitUntil { it == null })
        } finally {
            c.stop()
        }
    }

    @Test
    fun ac7_duplicates_returnEarliestCreated_andInvalidUrlIsAlwaysNull() {
        val first = add("https://a.com/", "first")
        now += 10
        add("https://a.com/", "second")
        assertEquals(first.id, run { repo.observeByUrl("https://a.com").first() }?.id)
        assertNull(run { repo.observeByUrl("javascript:1").first() })
        assertNull(run { repo.observeByUrl("").first() })
    }

    @Test
    fun ac7_differentPathOrQuery_isNotTheSameBookmark() {
        add("https://a.com/p", "p")
        assertNull(run { repo.observeByUrl("https://a.com/p/").first() })
        assertNull(run { repo.observeByUrl("https://a.com/p?x=1").first() })
    }

    // ---------- AC8 ----------

    @Test
    fun ac8_lastFolder_setAndRead_resetsToRootAfterDelete_ignoresInvalid() {
        val f = folder("F")
        val b = add("https://a.com")
        run { repo.setLastFolderId(f.id) }
        assertEquals(f.id, run { repo.lastFolderId() })
        run { repo.setLastFolderId(9_999L) }
        assertEquals(f.id, run { repo.lastFolderId() })
        run { repo.setLastFolderId(b.id) }
        assertEquals(f.id, run { repo.lastFolderId() })
        run { repo.delete(f.id).ok() }
        assertEquals(ROOT_ID, run { repo.lastFolderId() })
    }

    @Test
    fun ac8_lastFolder_resetsWhenAncestorDeleted() {
        val p = folder("P")
        val c = folder("C", p.id)
        run { repo.setLastFolderId(c.id) }
        run { repo.delete(p.id).ok() }
        assertEquals(ROOT_ID, run { repo.lastFolderId() })
    }

    @Test
    fun ac8_folderOptions_treeOrderWithDepth_andExcludeSubtree() {
        val a = folder("A")
        val a1 = folder("A1", a.id)
        val a1x = folder("A1x", a1.id)
        val b = folder("B")
        add("https://not-a-folder.com")
        assertEquals(
            listOf(
                FolderOption(ROOT_ID, "Dấu trang", 0),
                FolderOption(a.id, "A", 1),
                FolderOption(a1.id, "A1", 2),
                FolderOption(a1x.id, "A1x", 3),
                FolderOption(b.id, "B", 1),
            ),
            run { repo.folderOptions() },
        )
        assertEquals(
            listOf(FolderOption(ROOT_ID, "Dấu trang", 0), FolderOption(b.id, "B", 1)),
            run { repo.folderOptions(excludeSubtreeOf = a.id) },
        )
    }

    // ---------- AC9 ----------

    @Test
    fun ac9_search_matchesTitleOrUrl_caseInsensitive_acrossWholeTree() {
        val f = folder("F")
        add("https://a.com/", "Wikipedia", f.id)
        add("https://WIKI.org/", "Other")
        add("https://z.com/", "Nope")
        assertEquals(setOf("Wikipedia", "Other"), run { repo.search("wiki", 50) }.map { it.title }.toSet())
    }

    @Test
    fun ac9_search_multipleTermsAreAnd() {
        add("https://a.com/wiki", "Kotlin")
        add("https://b.com/wiki", "Java")
        assertEquals(listOf("Kotlin"), run { repo.search("wiki kotlin", 50) }.map { it.title })
    }

    @Test
    fun ac9_search_percentUnderscoreBackslashAreLiteral() {
        add("https://a.com/?p=100%", "pct")
        add("https://a.com/?p=1000", "no-pct")
        add("https://b.com/a_b", "under")
        add("https://b.com/axb", "no-under")
        add("https://c.com/a\\b", "back")
        add("https://c.com/axb2", "no-back")
        assertEquals(listOf("pct"), run { repo.search("100%", 50) }.map { it.title })
        assertEquals(listOf("under"), run { repo.search("a_b", 50) }.map { it.title })
        assertEquals(listOf("back"), run { repo.search("a\\b", 50) }.map { it.title })
    }

    @Test
    fun ac9_search_sortedByTitleCaseInsensitive_tiesByIdAscending() {
        val b2 = add("https://w1.com/", "banana")
        val a = add("https://w2.com/", "Apple")
        val b1 = add("https://w3.com/", "Banana")
        assertEquals(listOf(a.id, b2.id, b1.id), run { repo.search("w", 50) }.map { it.id })
    }

    @Test
    fun ac9_search_respectsLimit_andExcludesFolders() {
        repeat(5) { add("https://wiki$it.com/", "wiki $it") }
        folder("wiki folder")
        assertEquals(3, run { repo.search("wiki", 3) }.size)
        assertTrue(run { repo.search("wiki", 50) }.all { it.url.startsWith("https://") })
    }

    @Test
    fun ac9_suggest_sameMatchingRules_honoursLimit() {
        repeat(6) { add("https://wiki$it.com/", "t$it") }
        add("https://other.com/", "Kotlin")
        assertEquals(4, run { repo.suggest(listOf("wiki"), 4) }.size)
        assertEquals(listOf("Kotlin"), run { repo.suggest(listOf("KOTLIN"), 10) }.map { it.title })
        assertEquals(1, run { repo.suggest(listOf("other", "kotlin"), 10) }.size)
    }

    // ---------- import/export (AC16/AC17 phía repository) ----------

    @Test
    fun import_createsWrapperFolderUnderRoot_withWholeTree_returnsBookmarkCount() {
        val tree =
            ImportedFolder(
                "",
                listOf(
                    ImportedFolder(
                        "News",
                        listOf(
                            ImportedFolder("Deep", emptyList(), listOf(ImportedBookmark("D", "https://d.com/", null))),
                        ),
                        listOf(ImportedBookmark("N", "https://n.com/", 1_700_000_000L)),
                    ),
                ),
                listOf(ImportedBookmark("Top", "https://t.com/", null)),
            )
        assertEquals(BookmarkResult.Ok(3), run { repo.importTree(tree, "Đã nhập 2026-11-10") })
        val wrapper = children().single() as BookmarkFolder
        assertEquals("Đã nhập 2026-11-10", wrapper.title)
        assertEquals(listOf("News", "Top"), titles(wrapper.id))
        val news = children(wrapper.id).first()
        assertEquals(listOf("Deep", "N"), titles(news.id))
    }

    @Test
    fun import_twice_createsTwoSeparateFolders_noDedupe() {
        val tree = ImportedFolder("", emptyList(), listOf(ImportedBookmark("A", "https://a.com/", null)))
        run { repo.importTree(tree, "Đã nhập 1").ok() }
        run { repo.importTree(tree, "Đã nhập 1").ok() }
        assertEquals(2, children().size)
        assertEquals(2, run { repo.search("a.com", 10) }.size)
    }

    @Test
    fun import_exceedingCapacity_isFull_andIsAtomic() {
        maxNodes = 5
        add("https://keep.com", "keep")
        val tree =
            ImportedFolder("", emptyList(), (1..10).map { ImportedBookmark("b$it", "https://b$it.com/", null) })
        assertEquals(BookmarkResult.Full, run { repo.importTree(tree, "Đã nhập") })
        assertEquals(listOf("keep"), titles())
    }

    @Test
    fun export_returnsRootWithDisplayOrder() {
        val f2 = folder("F2")
        folder("F1")
        add("https://b.com", "B")
        add("https://a.com", "A", f2.id)
        val out = run { repo.exportTree() }
        assertEquals("Dấu trang", out.title)
        assertEquals(listOf("F2", "F1"), out.folders.map { it.title })
        assertEquals(listOf("B"), out.bookmarks.map { it.title })
        assertEquals(listOf("https://a.com"), out.folders[0].bookmarks.map { it.url })
    }
}
