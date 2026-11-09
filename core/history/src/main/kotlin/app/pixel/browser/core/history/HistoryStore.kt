package app.pixel.browser.core.history

import kotlinx.coroutines.flow.Flow

interface HistoryStore {
    suspend fun insert(
        url: String,
        title: String,
        visitedAt: Long,
    ): Long

    /** false nếu [id] không còn. */
    suspend fun touch(
        id: Long,
        title: String,
        visitedAt: Long,
    ): Boolean

    /** Mới nhất trước; [query] rỗng = tất cả. Nhiều từ cách nhau bằng khoảng trắng phải khớp tất cả. */
    fun entries(
        query: String,
        limit: Int,
    ): Flow<List<HistoryEntry>>

    /** Nhóm theo url; title của lượt mới nhất. */
    suspend fun suggest(
        terms: List<String>,
        candidateLimit: Int,
    ): List<HistoryHit>

    suspend fun delete(id: Long)

    suspend fun deleteBetween(
        fromInclusive: Long,
        toInclusive: Long,
    ): Int

    suspend fun prune(
        maxEntries: Int,
        olderThan: Long,
    ): Int

    suspend fun count(): Int
}
