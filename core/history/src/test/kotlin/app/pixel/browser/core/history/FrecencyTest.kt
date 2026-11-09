package app.pixel.browser.core.history

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 012 AC11. */
class FrecencyTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test
    fun justNow_oneVisit_is100() = assertEquals(100, Frecency.score(1, now, now))

    @Test
    fun threeVisits_fiveDaysAgo_is210() = assertEquals(210, Frecency.score(3, now - 5 * day, now))

    @Test
    fun visitCountIsCappedAt10_andOldWeightIs10() = assertEquals(100, Frecency.score(50, now - 100 * day, now))

    @Test
    fun fourDays_isInclusiveBoundary() = assertEquals(100, Frecency.score(1, now - 4 * day, now))

    @Test
    fun fourDaysAndOneMs_dropsTo70() = assertEquals(70, Frecency.score(1, now - 4 * day - 1, now))

    @Test
    fun otherBuckets() {
        assertEquals(70, Frecency.score(1, now - 14 * day, now))
        assertEquals(50, Frecency.score(1, now - 14 * day - 1, now))
        assertEquals(50, Frecency.score(1, now - 31 * day, now))
        assertEquals(30, Frecency.score(1, now - 31 * day - 1, now))
        assertEquals(30, Frecency.score(1, now - 90 * day, now))
        assertEquals(10, Frecency.score(1, now - 90 * day - 1, now))
    }

    @Test
    fun futureLastVisit_isTreatedAsAgeZero() = assertEquals(100, Frecency.score(1, now + 5 * day, now))

    @Test
    fun nonPositiveVisitCount_isZero() {
        assertEquals(0, Frecency.score(0, now, now))
        assertEquals(0, Frecency.score(-3, now, now))
    }
}
