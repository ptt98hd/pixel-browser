package app.pixel.browser.core.bookmarks

import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeChildren(folderId: Long): Flow<List<BookmarkNode>>

    fun observeByUrl(url: String): Flow<Bookmark?>

    suspend fun folder(id: Long): BookmarkFolder?

    /** Thêm so với spec: cần cho `BookmarkEditRoute(repo, bookmarkId, ...)` và nút "Sửa" của snackbar. */
    suspend fun bookmark(id: Long): Bookmark?

    suspend fun folderOptions(excludeSubtreeOf: Long? = null): List<FolderOption>

    suspend fun addBookmark(
        url: String,
        title: String,
        parentId: Long,
    ): BookmarkResult<Bookmark>

    suspend fun updateBookmark(
        id: Long,
        title: String,
        url: String,
        parentId: Long,
    ): BookmarkResult<Bookmark>

    suspend fun createFolder(
        parentId: Long,
        title: String,
    ): BookmarkResult<BookmarkFolder>

    suspend fun rename(
        id: Long,
        title: String,
    ): BookmarkResult<Unit>

    suspend fun move(
        id: Long,
        newParentId: Long,
    ): BookmarkResult<Unit>

    suspend fun delete(id: Long): BookmarkResult<DeletedTree>

    suspend fun restore(tree: DeletedTree): Boolean

    suspend fun search(
        query: String,
        limit: Int,
    ): List<Bookmark>

    suspend fun suggest(
        terms: List<String>,
        limit: Int,
    ): List<Bookmark>

    suspend fun importTree(
        root: ImportedFolder,
        folderTitle: String,
    ): BookmarkResult<Int>

    suspend fun exportTree(): ExportFolder

    /** Số nút con trực tiếp của từng thư mục (một truy vấn; thư mục không có con vắng mặt). */
    suspend fun childCounts(): Map<Long, Int>

    suspend fun lastFolderId(): Long

    suspend fun setLastFolderId(id: Long)
}
