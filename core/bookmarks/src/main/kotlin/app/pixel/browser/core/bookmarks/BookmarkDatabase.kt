package app.pixel.browser.core.bookmarks

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bảng `nodes`: `type` 0 = thư mục, 1 = dấu trang. Gốc hệ thống có id [ROOT_ID]. */
@Entity(tableName = "nodes", indices = [Index("parentId"), Index("urlKey")])
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val type: Int,
    val title: String,
    val url: String?,
    val urlKey: String?,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Kết quả `GROUP BY parentId` (không phải entity, không ảnh hưởng schema). */
data class ChildCount(
    val parentId: Long,
    val total: Int,
)

@Dao
interface NodeDao {
    @Query("SELECT COUNT(*) FROM nodes WHERE id != $ROOT_ID")
    suspend fun count(): Int

    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun get(id: Long): NodeEntity?

    @Query("SELECT * FROM nodes WHERE parentId = :parentId ORDER BY type, position, id")
    fun observeChildren(parentId: Long): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE type = 1 AND urlKey = :key ORDER BY createdAt, id LIMIT 1")
    fun observeByKey(key: String): Flow<NodeEntity?>

    @Query("SELECT * FROM nodes WHERE type = 0 ORDER BY position, id")
    suspend fun allFolders(): List<NodeEntity>

    @Query("SELECT * FROM nodes ORDER BY type, position, id")
    suspend fun allNodes(): List<NodeEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM nodes WHERE parentId = :parentId")
    suspend fun nextPosition(parentId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(node: NodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(nodes: List<NodeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(node: NodeEntity): Long

    @Query("UPDATE nodes SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun setTitle(
        id: Long,
        title: String,
        now: Long,
    )

    @Query(
        "UPDATE nodes SET title = :title, url = :url, urlKey = :urlKey, parentId = :parentId, " +
            "position = :position, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateBookmark(
        id: Long,
        title: String,
        url: String,
        urlKey: String,
        parentId: Long,
        position: Int,
        now: Long,
    )

    @Query("UPDATE nodes SET parentId = :parentId, position = :position WHERE id = :id")
    suspend fun moveTo(
        id: Long,
        parentId: Long,
        position: Int,
    )

    @Query("DELETE FROM nodes WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("SELECT id FROM nodes WHERE id IN (:ids)")
    suspend fun existing(ids: List<Long>): List<Long>

    @Query("SELECT parentId AS parentId, COUNT(*) AS total FROM nodes WHERE parentId IS NOT NULL GROUP BY parentId")
    suspend fun childCounts(): List<ChildCount>

    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<NodeEntity>

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: MetaEntity)
}

@Database(entities = [NodeEntity::class, MetaEntity::class], version = 1, exportSchema = true)
abstract class BookmarkDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
}

fun createBookmarkDatabase(
    context: Context,
    name: String = "bookmarks.db",
): BookmarkDatabase = Room.databaseBuilder(context.applicationContext, BookmarkDatabase::class.java, name).build()

/**
 * Kho dấu trang Room; cùng ngữ nghĩa với `InMemoryBookmarkRepository` (testFixtures). [maxNodes] không tính gốc hệ
 * thống; thư mục bọc của [importTree] nằm ngoài giới hạn này (parser đã chặn ở [BookmarkLimits.MAX_NODES]).
 * Gốc được tạo lười ở lần dùng đầu tiên (không cần callback khi dựng DB).
 */
class RoomBookmarkRepository(
    private val db: BookmarkDatabase,
    private val maxNodes: Int = BookmarkLimits.MAX_NODES,
    private val clock: () -> Long = System::currentTimeMillis,
) : BookmarkRepository {
    private val dao get() = db.nodeDao()
    private val seedLock = Mutex()

    @Volatile
    private var seeded = false

    private suspend fun ready() {
        if (seeded) return
        seedLock.withLock {
            if (!seeded) {
                dao.insertIfAbsent(
                    NodeEntity(ROOT_ID, null, TYPE_FOLDER, ROOT_TITLE, null, null, 0, 0L, 0L),
                )
                seeded = true
            }
        }
    }

    private suspend fun <T> tx(block: suspend () -> T): T {
        ready()
        return db.withTransaction { block() }
    }

    private fun NodeEntity.toNode(): BookmarkNode =
        if (type == TYPE_FOLDER) {
            BookmarkFolder(id, parentId, title, position, createdAt)
        } else {
            Bookmark(id, parentId, title, url.orEmpty(), position, createdAt, updatedAt)
        }

    private fun NodeEntity.toBookmark(): Bookmark = toNode() as Bookmark

    private fun NodeEntity.isFolder() = type == TYPE_FOLDER

    override fun observeChildren(folderId: Long): Flow<List<BookmarkNode>> =
        flow {
            ready()
            emitAll(dao.observeChildren(folderId).map { rows -> rows.map { it.toNode() } })
        }.distinctUntilChanged()

    override fun observeByUrl(url: String): Flow<Bookmark?> {
        val key = BookmarkRules.validUrl(url)?.let { BookmarkRules.urlKey(it) } ?: return flowOf(null)
        return flow {
            ready()
            emitAll(dao.observeByKey(key).map { it?.toBookmark() })
        }.distinctUntilChanged()
    }

    override suspend fun folder(id: Long): BookmarkFolder? {
        ready()
        return dao.get(id)?.takeIf { it.isFolder() }?.toNode() as BookmarkFolder?
    }

    override suspend fun bookmark(id: Long): Bookmark? {
        ready()
        return dao.get(id)?.takeIf { !it.isFolder() }?.toBookmark()
    }

    /** Độ sâu của thư mục [id] (gốc = 0). */
    private suspend fun depthOf(id: Long): Int {
        var depth = 0
        var cur = dao.get(id)
        while (cur?.parentId != null && depth <= DEPTH_GUARD) {
            depth++
            cur = dao.get(cur.parentId!!)
        }
        return depth
    }

    private fun List<NodeEntity>.childrenByParent(): Map<Long?, List<NodeEntity>> =
        sortedWith(compareBy<NodeEntity>({ it.type }, { it.position }, { it.id })).groupBy { it.parentId }

    /** Cây con của [id] theo thứ tự cha trước con (BFS); rỗng nếu [id] không tồn tại. */
    private suspend fun subtree(id: Long): List<NodeEntity> {
        val all = dao.allNodes()
        val byId = all.associateBy { it.id }
        val kids = all.childrenByParent()
        val root = byId[id] ?: return emptyList()
        val out = ArrayList<NodeEntity>()
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            out += cur
            kids[cur.id]?.let { queue.addAll(it) }
        }
        return out
    }

    override suspend fun folderOptions(excludeSubtreeOf: Long?): List<FolderOption> {
        ready()
        val folders = dao.allFolders().childrenByParent()
        val root = dao.get(ROOT_ID) ?: return emptyList()
        val out = ArrayList<FolderOption>()
        val stack = ArrayDeque<Pair<NodeEntity, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (node.id == excludeSubtreeOf) continue
            out += FolderOption(node.id, node.title, depth)
            folders[node.id]?.asReversed()?.forEach { stack.addLast(it to depth + 1) }
        }
        return out
    }

    override suspend fun addBookmark(
        url: String,
        title: String,
        parentId: Long,
    ): BookmarkResult<Bookmark> {
        val valid = BookmarkRules.validUrl(url) ?: return BookmarkResult.InvalidUrl
        return tx {
            if (dao.get(parentId)?.isFolder() != true) return@tx BookmarkResult.NotFound
            if (dao.count() >= maxNodes) return@tx BookmarkResult.Full
            val now = clock()
            val entity =
                NodeEntity(
                    parentId = parentId,
                    type = TYPE_BOOKMARK,
                    title = BookmarkRules.normalizeTitle(title, valid),
                    url = valid,
                    urlKey = BookmarkRules.urlKey(valid),
                    position = dao.nextPosition(parentId),
                    createdAt = now,
                    updatedAt = now,
                )
            BookmarkResult.Ok(entity.copy(id = dao.insert(entity)).toBookmark())
        }
    }

    override suspend fun updateBookmark(
        id: Long,
        title: String,
        url: String,
        parentId: Long,
    ): BookmarkResult<Bookmark> =
        tx {
            val old = dao.get(id)?.takeIf { !it.isFolder() } ?: return@tx BookmarkResult.NotFound
            val valid = BookmarkRules.validUrl(url) ?: return@tx BookmarkResult.InvalidUrl
            if (dao.get(parentId)?.isFolder() != true) return@tx BookmarkResult.NotFound
            val row =
                old.copy(
                    title = BookmarkRules.normalizeTitle(title, valid),
                    url = valid,
                    urlKey = BookmarkRules.urlKey(valid),
                    parentId = parentId,
                    position = if (parentId == old.parentId) old.position else dao.nextPosition(parentId),
                    updatedAt = clock(),
                )
            dao.updateBookmark(id, row.title, valid, row.urlKey.orEmpty(), parentId, row.position, row.updatedAt)
            BookmarkResult.Ok(row.toBookmark())
        }

    override suspend fun createFolder(
        parentId: Long,
        title: String,
    ): BookmarkResult<BookmarkFolder> {
        val clean = BookmarkRules.cleanTitle(title)
        if (clean.isEmpty()) return BookmarkResult.InvalidTitle
        return tx {
            if (dao.get(parentId)?.isFolder() != true) return@tx BookmarkResult.NotFound
            if (depthOf(parentId) + 1 > BookmarkLimits.MAX_DEPTH) return@tx BookmarkResult.TooDeep
            if (dao.count() >= maxNodes) return@tx BookmarkResult.Full
            val now = clock()
            val entity =
                NodeEntity(
                    parentId = parentId,
                    type = TYPE_FOLDER,
                    title = clean,
                    url = null,
                    urlKey = null,
                    position = dao.nextPosition(parentId),
                    createdAt = now,
                    updatedAt = now,
                )
            BookmarkResult.Ok(entity.copy(id = dao.insert(entity)).toNode() as BookmarkFolder)
        }
    }

    override suspend fun rename(
        id: Long,
        title: String,
    ): BookmarkResult<Unit> {
        if (id == ROOT_ID) return BookmarkResult.Protected
        val clean = BookmarkRules.cleanTitle(title)
        return tx {
            if (dao.get(id) == null) return@tx BookmarkResult.NotFound
            if (clean.isEmpty()) return@tx BookmarkResult.InvalidTitle
            dao.setTitle(id, clean, clock())
            BookmarkResult.Ok(Unit)
        }
    }

    override suspend fun move(
        id: Long,
        newParentId: Long,
    ): BookmarkResult<Unit> {
        if (id == ROOT_ID) return BookmarkResult.Protected
        return tx {
            val old = dao.get(id) ?: return@tx BookmarkResult.NotFound
            if (dao.get(newParentId)?.isFolder() != true) return@tx BookmarkResult.NotFound
            val sub = subtree(id)
            if (sub.any { it.id == newParentId }) return@tx BookmarkResult.Cycle
            if (old.isFolder()) {
                val kids = sub.filter { it.isFolder() }.groupBy { it.parentId }
                var below = 0
                var level = listOf(old)
                while (level.isNotEmpty()) {
                    level = level.flatMap { kids[it.id].orEmpty() }
                    if (level.isNotEmpty()) below++
                }
                if (depthOf(newParentId) + 1 + below > BookmarkLimits.MAX_DEPTH) return@tx BookmarkResult.TooDeep
            }
            val position = if (newParentId == old.parentId) old.position else dao.nextPosition(newParentId)
            dao.moveTo(id, newParentId, position)
            BookmarkResult.Ok(Unit)
        }
    }

    override suspend fun delete(id: Long): BookmarkResult<DeletedTree> {
        if (id == ROOT_ID) return BookmarkResult.Protected
        return tx {
            val doomed = subtree(id)
            if (doomed.isEmpty()) return@tx BookmarkResult.NotFound
            val ids = doomed.map { it.id }
            ids.chunked(SQL_CHUNK).forEach { dao.deleteAll(it) }
            if (readLastFolder() in ids) dao.putMeta(MetaEntity(META_LAST_FOLDER, ROOT_ID.toString()))
            BookmarkResult.Ok(DeletedTree(doomed.map { it.toNode() }))
        }
    }

    override suspend fun restore(tree: DeletedTree): Boolean =
        tx {
            val head = tree.nodes.firstOrNull() ?: return@tx false
            val ids = tree.nodes.map { it.id }
            if (ids.chunked(SQL_CHUNK).any { dao.existing(it).isNotEmpty() }) return@tx false
            val parent = head.parentId?.let { dao.get(it) }
            if (parent?.isFolder() != true) return@tx false
            val height = tree.foldersHeight() ?: return@tx false
            if (head is BookmarkFolder && depthOf(parent.id) + 1 + height > BookmarkLimits.MAX_DEPTH) return@tx false
            if (dao.count() + tree.nodes.size > maxNodes) return@tx false
            val rows =
                tree.nodes.map { n ->
                    when (n) {
                        is BookmarkFolder -> {
                            NodeEntity(
                                n.id,
                                n.parentId,
                                TYPE_FOLDER,
                                n.title,
                                null,
                                null,
                                n.position,
                                n.createdAt,
                                n.createdAt,
                            )
                        }

                        is Bookmark -> {
                            NodeEntity(
                                n.id,
                                n.parentId,
                                TYPE_BOOKMARK,
                                n.title,
                                n.url,
                                BookmarkRules.urlKey(n.url),
                                n.position,
                                n.createdAt,
                                n.updatedAt,
                            )
                        }
                    }
                }
            dao.insertAll(rows)
            true
        }

    /** [prefixTerm] != null → khớp đầu title/URL (bỏ scheme và `www.`) xếp trước, rồi theo [order]. */
    private suspend fun like(
        terms: List<String>,
        order: String,
        limit: Int,
        prefixTerm: String? = null,
    ): List<Bookmark> {
        val clean = terms.map { it.trim().take(MAX_TERM) }.filter { it.isNotEmpty() }
        if (clean.isEmpty() || limit <= 0) return emptyList()
        val args = ArrayList<Any>()
        val where =
            clean.joinToString(" AND ") { t ->
                val pattern = "%" + escapeLike(t) + "%"
                args += pattern
                args += pattern
                "(title LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\')"
            }
        var orderBy = order
        if (prefixTerm != null) {
            val p = escapeLike(prefixTerm.trim().take(MAX_TERM)) + "%"
            args.addAll(listOf(p, "http://$p", "https://$p", "http://www.$p", "https://www.$p"))
            orderBy =
                "CASE WHEN title LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' " +
                "OR url LIKE ? ESCAPE '\\' OR url LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END, $order"
        }
        args += limit
        ready()
        return dao
            .query(
                SimpleSQLiteQuery(
                    "SELECT * FROM nodes WHERE type = 1 AND $where ORDER BY $orderBy LIMIT ?",
                    args.toTypedArray(),
                ),
            ).map { it.toBookmark() }
    }

    private fun escapeLike(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<Bookmark> = like(query.trim().split(WHITESPACE), "title COLLATE NOCASE, id", limit)

    override suspend fun suggest(
        terms: List<String>,
        limit: Int,
    ): List<Bookmark> = like(terms, "createdAt DESC, id", limit, prefixTerm = terms.firstOrNull())

    override suspend fun importTree(
        root: ImportedFolder,
        folderTitle: String,
    ): BookmarkResult<Int> =
        tx {
            var size = 0
            val sizing = ArrayDeque(listOf(root))
            while (sizing.isNotEmpty()) {
                val f = sizing.removeLast()
                size += f.bookmarks.size + f.folders.size
                sizing.addAll(f.folders)
            }
            if (dao.count() + size > maxNodes) return@tx BookmarkResult.Full
            val now = clock()
            var imported = 0

            suspend fun addFolder(
                parent: Long,
                title: String,
            ): Long =
                dao.insert(
                    NodeEntity(
                        parentId = parent,
                        type = TYPE_FOLDER,
                        title = BookmarkRules.cleanTitle(title).ifEmpty { FALLBACK_FOLDER_TITLE },
                        url = null,
                        urlKey = null,
                        position = dao.nextPosition(parent),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )

            val wrapper = addFolder(ROOT_ID, folderTitle)
            val work = ArrayDeque<Pair<Long, ImportedFolder>>()
            work.addLast(wrapper to root)
            while (work.isNotEmpty()) {
                val (parent, folder) = work.removeFirst()
                folder.folders.forEach { work.addLast(addFolder(parent, it.title) to it) }
                var position = dao.nextPosition(parent)
                val rows =
                    folder.bookmarks.mapNotNull { b ->
                        val url = BookmarkRules.validUrl(b.url) ?: return@mapNotNull null
                        NodeEntity(
                            parentId = parent,
                            type = TYPE_BOOKMARK,
                            title = BookmarkRules.normalizeTitle(b.title, url),
                            url = url,
                            urlKey = BookmarkRules.urlKey(url),
                            position = position++,
                            createdAt = b.addedAtSeconds?.takeIf { it > 0 }?.let { it * MILLIS } ?: now,
                            updatedAt = now,
                        )
                    }
                rows.chunked(SQL_CHUNK).forEach { dao.insertAll(it) }
                imported += rows.size
            }
            BookmarkResult.Ok(imported)
        }

    override suspend fun exportTree(): ExportFolder {
        ready()
        val all = dao.allNodes()
        val kids = all.childrenByParent()

        fun build(node: NodeEntity): ExportFolder {
            val children = kids[node.id].orEmpty()
            return ImportedFolder(
                node.title,
                children.filter { it.isFolder() }.map { build(it) },
                children.filter { !it.isFolder() }.map {
                    ImportedBookmark(
                        it.title,
                        it.url.orEmpty(),
                        it.createdAt / MILLIS,
                    )
                },
            )
        }
        val root = all.firstOrNull { it.id == ROOT_ID } ?: return ImportedFolder(ROOT_TITLE, emptyList(), emptyList())
        return build(root)
    }

    private suspend fun readLastFolder(): Long = dao.meta(META_LAST_FOLDER)?.toLongOrNull() ?: ROOT_ID

    override suspend fun childCounts(): Map<Long, Int> {
        ready()
        return dao.childCounts().associate { it.parentId to it.total }
    }

    override suspend fun lastFolderId(): Long {
        ready()
        val id = readLastFolder()
        return if (dao.get(id)?.isFolder() == true) id else ROOT_ID
    }

    override suspend fun setLastFolderId(id: Long) {
        ready()
        if (dao.get(id)?.isFolder() == true) dao.putMeta(MetaEntity(META_LAST_FOLDER, id.toString()))
    }

    private companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_BOOKMARK = 1
        const val ROOT_TITLE = "Dấu trang"
        const val META_LAST_FOLDER = "last_folder_id"
        const val FALLBACK_FOLDER_TITLE = "-"
        const val SQL_CHUNK = 500
        const val MAX_TERM = 256
        const val DEPTH_GUARD = 64
        const val MILLIS = 1000L
        val WHITESPACE = Regex("\\s+")
    }
}
