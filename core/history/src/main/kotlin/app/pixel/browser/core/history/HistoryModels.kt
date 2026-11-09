package app.pixel.browser.core.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Một lượt truy cập (spec 012). [visitedAt] là epoch ms. */
data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

/** Tổng hợp theo URL cho gợi ý/frecency. */
data class HistoryHit(
    val url: String,
    val title: String,
    val visitCount: Int,
    val lastVisitedAt: Long,
)

data class HistoryDay(
    val date: LocalDate,
    val entries: List<HistoryEntry>,
)

object HistoryRetention {
    const val MAX_ENTRIES = 20_000
    const val MAX_AGE_DAYS = 90
    const val MAX_URL = 2048
    const val MAX_TITLE = 200
}

private const val HOUR_MS = 3_600_000L
internal const val DAY_MS = 86_400_000L

enum class HistoryRange {
    LastHour,
    LastDay,
    LastWeek,
    All,
    ;

    /** Mốc bắt đầu (epoch ms) của khoảng tính từ [now]; `All` → 0. */
    fun from(now: Long): Long =
        when (this) {
            LastHour -> now - HOUR_MS
            LastDay -> now - DAY_MS
            LastWeek -> now - 7 * DAY_MS
            All -> 0L
        }
}

/** Nhóm theo ngày (theo [zone]); giữ thứ tự mới nhất trước. */
fun groupByDay(
    entries: List<HistoryEntry>,
    zone: ZoneId,
): List<HistoryDay> =
    entries
        .groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
        .map { (date, list) -> HistoryDay(date, list) }
        .sortedByDescending { it.date }

object Frecency {
    private const val MAX_VISITS = 10

    fun score(
        visitCount: Int,
        lastVisitedAt: Long,
        now: Long,
    ): Int {
        if (visitCount <= 0) return 0
        val age = (now - lastVisitedAt).coerceAtLeast(0)
        val weight =
            when {
                age <= 4 * DAY_MS -> 100
                age <= 14 * DAY_MS -> 70
                age <= 31 * DAY_MS -> 50
                age <= 90 * DAY_MS -> 30
                else -> 10
            }
        return minOf(visitCount, MAX_VISITS) * weight
    }
}
