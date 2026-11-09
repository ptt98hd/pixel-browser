package app.pixel.browser.core.bookmarks

const val ROOT_ID = 1L

sealed interface BookmarkNode {
    val id: Long
    val parentId: Long?
    val title: String
    val position: Int
    val createdAt: Long
}

data class BookmarkFolder(
    override val id: Long,
    override val parentId: Long?,
    override val title: String,
    override val position: Int,
    override val createdAt: Long,
) : BookmarkNode

data class Bookmark(
    override val id: Long,
    override val parentId: Long?,
    override val title: String,
    val url: String,
    override val position: Int,
    override val createdAt: Long,
    val updatedAt: Long,
) : BookmarkNode

/** Mục cho bộ chọn thư mục; [depth] = 0 cho gốc. */
data class FolderOption(
    val id: Long,
    val title: String,
    val depth: Int,
)

/**
 * Cây con đã xóa để hoàn tác: [nodes] theo thứ tự cha trước con, phần tử đầu là gốc cây (giữ nguyên id/parentId/position).
 * Constructor công khai (spec ghi `internal`) để fixture ở `testFixtures` và test UI tạo được.
 */
class DeletedTree(
    val nodes: List<BookmarkNode>,
) {
    val rootId: Long get() = nodes.first().id

    /**
     * Chiều cao tương đối của các thư mục trong cây (gốc cây = 0), hoặc `null` nếu cây không hợp lệ: id trùng, nút
     * ngoài gốc có cha nằm ngoài cây (hoặc không phải thư mục / xếp sau con), hoặc dấu trang có URL không hợp lệ.
     * `restore` dùng để chặn cây giả mạo/hỏng.
     */
    fun foldersHeight(): Int? {
        val first = nodes.firstOrNull() ?: return null
        if (nodes.map { it.id }.toSet().size != nodes.size) return null
        val depth = HashMap<Long, Int>()
        val folders = HashSet<Long>()
        var height = 0
        for ((index, node) in nodes.withIndex()) {
            val level = if (index == 0) 0 else (depth[node.parentId]?.plus(1) ?: return null)
            if (index > 0 && node.parentId !in folders) return null
            depth[node.id] = level
            when (node) {
                is BookmarkFolder -> {
                    folders += node.id
                    height = maxOf(height, level)
                }

                is Bookmark -> {
                    if (BookmarkRules.validUrl(node.url) == null) return null
                }
            }
        }
        return height.takeIf { first.id == rootId }
    }
}

sealed interface BookmarkResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : BookmarkResult<T>

    data object InvalidUrl : BookmarkResult<Nothing>

    data object InvalidTitle : BookmarkResult<Nothing>

    data object NotFound : BookmarkResult<Nothing>

    data object Protected : BookmarkResult<Nothing>

    data object Cycle : BookmarkResult<Nothing>

    data object TooDeep : BookmarkResult<Nothing>

    data object Full : BookmarkResult<Nothing>
}

object BookmarkLimits {
    const val MAX_NODES = 20_000
    const val MAX_DEPTH = 16
    const val MAX_TITLE = 200
    const val MAX_URL = 2048
    const val MAX_IMPORT_BYTES = 10 * 1024 * 1024
}

data class ImportedBookmark(
    val title: String,
    val url: String,
    val addedAtSeconds: Long?,
)

data class ImportedFolder(
    val title: String,
    val folders: List<ImportedFolder>,
    val bookmarks: List<ImportedBookmark>,
)

typealias ExportFolder = ImportedFolder
