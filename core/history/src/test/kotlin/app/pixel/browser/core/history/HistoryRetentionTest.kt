package app.pixel.browser.core.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 012 AC10 – dọn lịch sử sau mỗi 100 lần ghi. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRetentionTest {
    private class PruneSpy(
        private val delegate: InMemoryHistoryStore = InMemoryHistoryStore(),
    ) : HistoryStore by delegate {
        val prunes = mutableListOf<Pair<Int, Long>>()

        override suspend fun prune(
            maxEntries: Int,
            olderThan: Long,
        ): Int {
            prunes += maxEntries to olderThan
            return delegate.prune(maxEntries, olderThan)
        }
    }

    private val day = 86_400_000L
    private val now = 1_000 * day

    private suspend fun kotlinx.coroutines.test.TestScope.recordDistinct(
        r: HistoryRecorder,
        n: Int,
    ) {
        repeat(n) { r.record(PageVisit("t", "https://site$it.com/", "T", isPrivate = false, isError = false)) }
        advanceUntilIdle()
    }

    @Test
    fun constants_matchSpec() {
        assertEquals(20_000, HistoryRetention.MAX_ENTRIES)
        assertEquals(90, HistoryRetention.MAX_AGE_DAYS)
        assertEquals(2048, HistoryRetention.MAX_URL)
        assertEquals(200, HistoryRetention.MAX_TITLE)
    }

    @Test
    fun ac10_after100Writes_pruneCalledExactlyOnce_withMaxEntriesAnd90DayCutoff() =
        runTest {
            val spy = PruneSpy()
            recordDistinct(HistoryRecorder(spy, this) { now }, 100)
            assertEquals(listOf(HistoryRetention.MAX_ENTRIES to now - 90 * day), spy.prunes)
        }

    @Test
    fun ac10_under100Writes_pruneNotCalled() =
        runTest {
            val spy = PruneSpy()
            recordDistinct(HistoryRecorder(spy, this) { now }, 99)
            assertEquals(emptyList<Pair<Int, Long>>(), spy.prunes)
        }

    @Test
    fun ac10_200Writes_pruneCalledTwice() =
        runTest {
            val spy = PruneSpy()
            recordDistinct(HistoryRecorder(spy, this) { now }, 200)
            assertEquals(2, spy.prunes.size)
        }
}
