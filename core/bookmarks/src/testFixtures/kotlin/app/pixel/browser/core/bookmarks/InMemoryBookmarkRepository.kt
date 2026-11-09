package app.pixel.browser.core.bookmarks

import app.pixel.browser.core.omnibox.UrlDisplay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [BookmarkRepository] chạy trên bộ nhớ, thỏa cùng hợp đồng với `RoomBookmarkRepository` (spec 013 AC2-AC9).
 * [maxNodes] không tính gốc hệ thống. [failWith] khác null → mọi thao tác suspend ném lỗi đó.
 */
class InMemoryBookmarkRepository(
    private val maxNodes: Int = BookmarkLimits.MAX_NODES,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {
    private data class Row(
        val id: Long,
        val parentId: Long?,
        val isFolder: Boolean,
        val title: String,
        val url: String?,
        val position: Int,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private data class State(
        val rows: Map<Long, Row>,
        val nextId: Long,
        val lastFolder: Long,
    )

    private val state =
        MutableStateFlow(
            State(
                mapOf(ROOT_ID to Row(ROOT_ID, null, true, ROOT_TITLE, null, 0, 0L, 0L)),
                ROOT_ID + 1,
                ROOT_ID,
            ),
        )

    @Volatile
    var failWith: Throwable? = null

    private fun check() {
        failWith?.let { throw it }
    }

    private fun Row.toNode(): BookmarkNode =
        if (isFolder) {
            BookmarkFolder(id, parentId, title, position, createdAt)
        } else {
            Bookmark(id, parentId, title, url!!, position, createdAt, updatedAt)
        }

    private fun Row.toBookmark(): Bookmark = toNode() as Bookmark

    private fun Map<Long, Row>.childrenOf(folderId: Long): List<Row> =
        values
            .filter { it.parentId == folderId }
            .sortedWith(compareBy<Row>({ !it.isFolder }, { it.position }, { it.id }))

    private fun Map<Long, Row>.depthOf(id: Long): Int {
        var depth = 0
        var cur = this[id]
        while (cur?.parentId != null) {
            depth++
            cur = this[cur.parentId]
        }
        return depth
    }

    private fun Map<Long, Row>.subtree(id: Long): List<Row> {
        val out = mutableListOf<Row>()
        val queue = ArrayDeque(listOf(id))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val row = this[cur] ?: continue
            out += row
            childrenOf(cur).forEach { queue.addLast(it.id) }
        }
        return out
    }

    private fun Map<Long, Row>.nextPosition(parentId: Long): Int =
        (values.filter { it.parentId == parentId }.maxOfOrNull { it.position } ?: -1) + 1

    private fun State.count(): Int = rows.size - 1

    override fun observeChildren(folderId: Long): Flow<List<BookmarkNode>> =
        state.map { s -> s.rows.childrenOf(folderId).map { it.toNode() } }.distinctUntilChanged()

    override fun observeByUrl(url: String): Flow<Bookmark?> {
        val valid = BookmarkRules.validUrl(url)
        val key = valid?.let { BookmarkRules.urlKey(it) }
        return state
            .map { s ->
                if (key == null) {
                    null
                } else {
                    s.rows.values
                        .filter { !it.isFolder && BookmarkRules.urlKey(it.url!!) == key }
                        .minWithOrNull(compareBy<Row>({ it.createdAt }, { it.id }))
                        ?.toBookmark()
                }
            }.distinctUntilChanged()
    }

    override suspend fun folder(id: Long): BookmarkFolder? {
        check()
        return state.value.rows[id]
            ?.takeIf { it.isFolder }
            ?.toNode() as BookmarkFolder?
    }

    override suspend fun bookmark(id: Long): Bookmark? {
        check()
        return state.value.rows[id]
            ?.takeIf { !it.isFolder }
            ?.toBookmark()
    }

    override suspend fun folderOptions(excludeSubtreeOf: Long?): List<FolderOption> {
        check()
        val rows = state.value.rows
        val out = mutableListOf<FolderOption>()

        fun walk(
            id: Long,
            depth: Int,
        ) {
            if (id == excludeSubtreeOf) return
            val row = rows.getValue(id)
            out += FolderOption(id, row.title, depth)
            rows.childrenOf(id).filter { it.isFolder }.forEach { walk(it.id, depth + 1) }
        }
        walk(ROOT_ID, 0)
        return out
    }

    @Synchronized
    private fun <T> mutate(block: (State) -> Pair<State, T>): T {
        val (next, result) = block(state.value)
        state.value = next
        return result
    }

    override suspend fun addBookmark(
        url: String,
        title: String,
        parentId: Long,
    ): BookmarkResult<Bookmark> {
        check()
        val valid = BookmarkRules.validUrl(url) ?: return BookmarkResult.InvalidUrl
        return mutate { s ->
            if (s.rows[parentId]?.isFolder != true) return@mutate s to BookmarkResult.NotFound
            if (s.count() >= maxNodes) return@mutate s to BookmarkResult.Full
            val now = clock()
            val row =
                Row(
                    s.nextId,
                    parentId,
                    false,
                    BookmarkRules.normalizeTitle(title, valid),
                    valid,
                    s.rows.nextPosition(parentId),
                    now,
                    now,
                )
            s.copy(rows = s.rows + (row.id to row), nextId = s.nextId + 1) to BookmarkResult.Ok(row.toBookmark())
        }
    }

    override suspend fun updateBookmark(
        id: Long,
        title: String,
        url: String,
        parentId: Long,
    ): BookmarkResult<Bookmark> {
        check()
        return mutate { s ->
            val old = s.rows[id]?.takeIf { !it.isFolder } ?: return@mutate s to BookmarkResult.NotFound
            val valid = BookmarkRules.validUrl(url) ?: return@mutate s to BookmarkResult.InvalidUrl
            if (s.rows[parentId]?.isFolder != true) return@mutate s to BookmarkResult.NotFound
            val row =
                old.copy(
                    title = BookmarkRules.normalizeTitle(title, valid),
                    url = valid,
                    parentId = parentId,
                    position = if (parentId == old.parentId) old.position else s.rows.nextPosition(parentId),
                    updatedAt = clock(),
                )
            s.copy(rows = s.rows + (id to row)) to BookmarkResult.Ok(row.toBookmark())
        }
    }

    override suspend fun createFolder(
        parentId: Long,
        title: String,
    ): BookmarkResult<BookmarkFolder> {
        check()
        val clean = BookmarkRules.cleanTitle(title)
        if (clean.isEmpty()) return BookmarkResult.InvalidTitle
        return mutate { s ->
            if (s.rows[parentId]?.isFolder != true) return@mutate s to BookmarkResult.NotFound
            if (s.rows.depthOf(parentId) + 1 > BookmarkLimits.MAX_DEPTH) return@mutate s to BookmarkResult.TooDeep
            if (s.count() >= maxNodes) return@mutate s to BookmarkResult.Full
            val now = clock()
            val row = Row(s.nextId, parentId, true, clean, null, s.rows.nextPosition(parentId), now, now)
            s.copy(rows = s.rows + (row.id to row), nextId = s.nextId + 1) to
                BookmarkResult.Ok(row.toNode() as BookmarkFolder)
        }
    }

    override suspend fun rename(
        id: Long,
        title: String,
    ): BookmarkResult<Unit> {
        check()
        if (id == ROOT_ID) return BookmarkResult.Protected
        val clean = BookmarkRules.cleanTitle(title)
        return mutate { s ->
            val old = s.rows[id] ?: return@mutate s to BookmarkResult.NotFound
            if (clean.isEmpty()) return@mutate s to BookmarkResult.InvalidTitle
            s.copy(rows = s.rows + (id to old.copy(title = clean, updatedAt = clock()))) to BookmarkResult.Ok(Unit)
        }
    }

    override suspend fun move(
        id: Long,
        newParentId: Long,
    ): BookmarkResult<Unit> {
        check()
        if (id == ROOT_ID) return BookmarkResult.Protected
        return mutate { s ->
            val old = s.rows[id] ?: return@mutate s to BookmarkResult.NotFound
            if (s.rows[newParentId]?.isFolder != true) return@mutate s to BookmarkResult.NotFound
            val inSubtree = s.rows.subtree(id).any { it.id == newParentId }
            if (inSubtree) return@mutate s to BookmarkResult.Cycle
            if (old.isFolder) {
                val below =
                    s.rows
                        .subtree(
                            id,
                        ).filter { it.isFolder }
                        .maxOf { s.rows.depthOf(it.id) - s.rows.depthOf(id) }
                if (s.rows.depthOf(newParentId) + 1 + below > BookmarkLimits.MAX_DEPTH) {
                    return@mutate s to BookmarkResult.TooDeep
                }
            }
            val moved =
                old.copy(
                    parentId = newParentId,
                    position = if (newParentId == old.parentId) old.position else s.rows.nextPosition(newParentId),
                )
            s.copy(rows = s.rows + (id to moved)) to BookmarkResult.Ok(Unit)
        }
    }

    override suspend fun delete(id: Long): BookmarkResult<DeletedTree> {
        check()
        if (id == ROOT_ID) return BookmarkResult.Protected
        return mutate { s ->
            if (s.rows[id] == null) return@mutate s to BookmarkResult.NotFound
            val doomed = s.rows.subtree(id)
            val tree = DeletedTree(doomed.map { it.toNode() })
            val lastGone = s.lastFolder in doomed.map { it.id }
            s.copy(
                rows = s.rows - doomed.map { it.id }.toSet(),
                lastFolder = if (lastGone) ROOT_ID else s.lastFolder,
            ) to BookmarkResult.Ok(tree)
        }
    }

    override suspend fun restore(tree: DeletedTree): Boolean {
        check()
        return mutate { s ->
            val rootNode = tree.nodes.firstOrNull() ?: return@mutate s to false
            if (tree.nodes.any { it.id in s.rows }) return@mutate s to false
            if (s.rows[rootNode.parentId]?.isFolder != true) return@mutate s to false
            val height = tree.foldersHeight() ?: return@mutate s to false
            if (rootNode is BookmarkFolder &&
                s.rows.depthOf(rootNode.parentId!!) + 1 + height > BookmarkLimits.MAX_DEPTH
            ) {
                return@mutate s to false
            }
            if (s.count() + tree.nodes.size > maxNodes) return@mutate s to false
            val restored =
                tree.nodes.associate { n ->
                    n.id to
                        when (n) {
                            is BookmarkFolder -> {
                                Row(
                                    n.id,
                                    n.parentId,
                                    true,
                                    n.title,
                                    null,
                                    n.position,
                                    n.createdAt,
                                    n.createdAt,
                                )
                            }

                            is Bookmark -> {
                                Row(
                                    n.id,
                                    n.parentId,
                                    false,
                                    n.title,
                                    n.url,
                                    n.position,
                                    n.createdAt,
                                    n.updatedAt,
                                )
                            }
                        }
                }
            s.copy(rows = s.rows + restored, nextId = maxOf(s.nextId, restored.keys.max() + 1)) to true
        }
    }

    private fun matches(
        row: Row,
        terms: List<String>,
    ): Boolean =
        !row.isFolder &&
            terms.all { row.title.contains(it, ignoreCase = true) || row.url!!.contains(it, ignoreCase = true) }

    private fun startsWithTerm(
        row: Row,
        term: String,
    ): Boolean =
        row.title.startsWith(term, ignoreCase = true) ||
            UrlDisplay.host(row.url.orEmpty()).startsWith(term, ignoreCase = true)

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<Bookmark> {
        check()
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return state.value.rows.values
            .filter { matches(it, terms) }
            .sortedWith(compareBy<Row>({ it.title.lowercase() }, { it.id }))
            .take(limit.coerceAtLeast(0))
            .map { it.toBookmark() }
    }

    override suspend fun suggest(
        terms: List<String>,
        limit: Int,
    ): List<Bookmark> {
        check()
        if (terms.isEmpty()) return emptyList()
        return state.value.rows.values
            .filter { matches(it, terms) }
            .sortedWith(compareBy<Row>({ !startsWithTerm(it, terms.first()) }, { -it.createdAt }, { it.id }))
            .take(limit.coerceAtLeast(0))
            .map { it.toBookmark() }
    }

    override suspend fun importTree(
        root: ImportedFolder,
        folderTitle: String,
    ): BookmarkResult<Int> {
        check()
        return mutate { s ->
            fun size(f: ImportedFolder): Int = f.bookmarks.size + f.folders.sumOf { 1 + size(it) }
            if (s.count() + size(root) > maxNodes) return@mutate s to BookmarkResult.Full
            var rows = s.rows
            var next = s.nextId
            var imported = 0
            val now = clock()

            fun add(
                parent: Long,
                folder: ImportedFolder,
            ) {
                folder.folders.forEach { f ->
                    val id = next++
                    rows = rows + (id to Row(id, parent, true, f.title, null, rows.nextPosition(parent), now, now))
                    add(id, f)
                }
                folder.bookmarks.forEach { b ->
                    val id = next++
                    val created = createdAtOf(b, now)
                    rows =
                        rows + (id to Row(id, parent, false, b.title, b.url, rows.nextPosition(parent), created, now))
                    imported++
                }
            }
            val wrapper = next++
            rows =
                rows + (wrapper to Row(wrapper, ROOT_ID, true, folderTitle, null, rows.nextPosition(ROOT_ID), now, now))
            add(wrapper, root)
            s.copy(rows = rows, nextId = next) to BookmarkResult.Ok(imported)
        }
    }

    override suspend fun exportTree(): ExportFolder {
        check()
        val rows = state.value.rows

        fun build(id: Long): ExportFolder {
            val kids = rows.childrenOf(id)
            return ImportedFolder(
                rows.getValue(id).title,
                kids.filter { it.isFolder }.map { build(it.id) },
                kids.filter { !it.isFolder }.map { ImportedBookmark(it.title, it.url!!, it.createdAt / 1000) },
            )
        }
        return build(ROOT_ID)
    }

    override suspend fun childCounts(): Map<Long, Int> {
        check()
        return state.value.rows.values
            .mapNotNull { it.parentId }
            .groupingBy { it }
            .eachCount()
    }

    override suspend fun lastFolderId(): Long {
        check()
        return state.value.lastFolder
    }

    override suspend fun setLastFolderId(id: Long) {
        check()
        mutate { s -> (if (s.rows[id]?.isFolder == true) s.copy(lastFolder = id) else s) to Unit }
    }

    private fun createdAtOf(
        b: ImportedBookmark,
        now: Long,
    ): Long = b.addedAtSeconds?.takeIf { it > 0 }?.let { it * 1000 } ?: now

    companion object {
        const val ROOT_TITLE = "Dấu trang"
    }
}
