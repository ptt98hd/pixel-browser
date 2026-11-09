package app.pixel.browser.core.history

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [HistoryStore] chạy trên bộ nhớ, thỏa cùng hợp đồng với `RoomHistoryStore` (spec 012 AC2).
 * Khớp chuỗi: không phân biệt hoa thường, nhiều từ cách nhau bằng khoảng trắng là AND, ký tự `%`/`_`/`\` là thường.
 */
class InMemoryHistoryStore : HistoryStore {
    private val rows = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var nextId = 1L

    /** Nếu khác null, mọi thao tác ghi/đọc suspend ném lỗi này (test độ bền, AC5/AC12). */
    @Volatile
    var failWith: Throwable? = null

    private fun check() {
        failWith?.let { throw it }
    }

    private val newestFirst = compareByDescending<HistoryEntry> { it.visitedAt }.thenByDescending { it.id }

    override suspend fun insert(
        url: String,
        title: String,
        visitedAt: Long,
    ): Long {
        check()
        synchronized(this) {
            val id = nextId++
            rows.value = rows.value + HistoryEntry(id, url, title, visitedAt)
            return id
        }
    }

    override suspend fun touch(
        id: Long,
        title: String,
        visitedAt: Long,
    ): Boolean {
        check()
        synchronized(this) {
            if (rows.value.none { it.id == id }) return false
            rows.value = rows.value.map { if (it.id == id) it.copy(title = title, visitedAt = visitedAt) else it }
            return true
        }
    }

    private fun matches(
        entry: HistoryEntry,
        terms: List<String>,
    ): Boolean =
        terms.all { t ->
            entry.url.contains(t, ignoreCase = true) || entry.title.contains(t, ignoreCase = true)
        }

    override fun entries(
        query: String,
        limit: Int,
    ): Flow<List<HistoryEntry>> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return rows.map { all -> all.filter { matches(it, terms) }.sortedWith(newestFirst).take(limit) }
    }

    override suspend fun suggest(
        terms: List<String>,
        candidateLimit: Int,
    ): List<HistoryHit> {
        check()
        return rows.value
            .filter { matches(it, terms) }
            .groupBy { it.url }
            .map { (url, visits) ->
                val latest = visits.sortedWith(newestFirst).first()
                HistoryHit(url, latest.title, visits.size, latest.visitedAt)
            }.sortedByDescending { it.lastVisitedAt }
            .take(candidateLimit)
    }

    override suspend fun delete(id: Long) {
        check()
        synchronized(this) { rows.value = rows.value.filterNot { it.id == id } }
    }

    override suspend fun deleteBetween(
        fromInclusive: Long,
        toInclusive: Long,
    ): Int {
        check()
        synchronized(this) {
            val (gone, kept) = rows.value.partition { it.visitedAt in fromInclusive..toInclusive }
            rows.value = kept
            return gone.size
        }
    }

    override suspend fun prune(
        maxEntries: Int,
        olderThan: Long,
    ): Int {
        check()
        synchronized(this) {
            val before = rows.value.size
            var kept = rows.value.filter { it.visitedAt >= olderThan }.sortedWith(newestFirst)
            if (kept.size > maxEntries) kept = kept.take(maxEntries)
            rows.value = kept
            return before - kept.size
        }
    }

    override suspend fun count(): Int = rows.value.size
}
