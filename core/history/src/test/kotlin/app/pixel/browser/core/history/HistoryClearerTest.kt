package app.pixel.browser.core.history

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 012 AC9. */
class HistoryClearerTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L
    private val day = 86_400_000L

    @Test
    fun rangeFrom_matchesSpec() {
        assertEquals(now - hour, HistoryRange.LastHour.from(now))
        assertEquals(now - day, HistoryRange.LastDay.from(now))
        assertEquals(now - 7 * day, HistoryRange.LastWeek.from(now))
        assertEquals(0L, HistoryRange.All.from(now))
    }

    @Test
    fun clearLastHour_removesBoundaryEntry_keepsOlderOne_returnsCount() =
        runTest {
            val store = InMemoryHistoryStore()
            store.insert("https://boundary.com/", "", now - hour)
            store.insert("https://older.com/", "", now - hour - 1)
            store.insert("https://recent.com/", "", now - 1_000)
            val removed = HistoryClearer(store, { now }).clear(HistoryRange.LastHour)
            assertEquals(2, removed)
            assertEquals(listOf("https://older.com/"), store.entries("", 10).first().map { it.url })
        }

    @Test
    fun clearAll_removesEverything() =
        runTest {
            val store = InMemoryHistoryStore()
            store.insert("https://a.com/", "", 5)
            store.insert("https://b.com/", "", now)
            assertEquals(2, HistoryClearer(store, { now }).clear(HistoryRange.All))
            assertEquals(0, store.count())
        }
}
