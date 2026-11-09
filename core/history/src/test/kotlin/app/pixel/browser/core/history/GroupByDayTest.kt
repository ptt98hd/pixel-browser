package app.pixel.browser.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Spec 012 AC8. */
class GroupByDayTest {
    private val utc = ZoneId.of("UTC")

    private fun at(
        zone: ZoneId,
        y: Int,
        m: Int,
        d: Int,
        h: Int,
        min: Int,
    ): Long =
        LocalDateTime
            .of(y, m, d, h, min)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun e(
        id: Long,
        at: Long,
    ) = HistoryEntry(id, "https://$id.com/", "", at)

    @Test
    fun threeEntries_twoDays_newestDayFirst_orderKept() {
        val list =
            listOf(
                e(1, at(utc, 2026, 11, 10, 23, 59)),
                e(2, at(utc, 2026, 11, 10, 0, 1)),
                e(3, at(utc, 2026, 11, 9, 23, 50)),
            )
        val days = groupByDay(list, utc)
        assertEquals(listOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9)), days.map { it.date })
        assertEquals(listOf(1L, 2L), days[0].entries.map { it.id })
        assertEquals(listOf(3L), days[1].entries.map { it.id })
    }

    @Test
    fun empty_returnsEmptyList() = assertEquals(emptyList<HistoryDay>(), groupByDay(emptyList(), utc))

    @Test
    fun dayBoundaryFollowsZone() {
        val ts = at(utc, 2026, 11, 10, 23, 30)
        val inUtc = groupByDay(listOf(e(1, ts)), utc).single().date
        val inTokyo = groupByDay(listOf(e(1, ts)), ZoneId.of("Asia/Tokyo")).single().date
        assertEquals(LocalDate.of(2026, 11, 10), inUtc)
        assertEquals(LocalDate.of(2026, 11, 11), inTokyo)
    }

    @Test
    fun dstChangeDay_entriesStayOnTheirLocalDate() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29: Berlin chuyển sang giờ mùa hè (ngày chỉ có 23 giờ).
        val late = e(1, at(berlin, 2026, 3, 29, 23, 59))
        val early = e(2, at(berlin, 2026, 3, 29, 0, 30))
        val prev = e(3, at(berlin, 2026, 3, 28, 23, 30))
        val days = groupByDay(listOf(late, early, prev), berlin)
        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 3, 29), days[0].date)
        assertEquals(listOf(1L, 2L), days[0].entries.map { it.id })
        assertEquals(LocalDate.of(2026, 3, 28), days[1].date)
        assertTrue(days.all { it.entries.isNotEmpty() })
    }
}
