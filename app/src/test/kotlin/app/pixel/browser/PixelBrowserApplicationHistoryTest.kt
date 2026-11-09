package app.pixel.browser

import app.pixel.browser.core.history.HistoryRetention
import app.pixel.browser.core.history.HistoryStore
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.history.PageVisit
import app.pixel.browser.core.history.RoomHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/** Spec 012 AC10 (phần `:app`) – lịch sử được dọn một lần khi gắn store; `onCreate` gắn Room trên `ioScope`. */
@RunWith(org.robolectric.RobolectricTestRunner::class)
class PixelBrowserApplicationHistoryTest {
    private class PruneSpy(
        private val delegate: InMemoryHistoryStore = InMemoryHistoryStore(),
    ) : HistoryStore by delegate {
        val prunes = mutableListOf<Pair<Int, Long>>()

        override suspend fun prune(
            maxEntries: Int,
            olderThan: Long,
        ): Int {
            prunes += maxEntries to olderThan
            return 0
        }
    }

    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    @Test
    fun onCreate_bindsRoomHistoryStore() {
        assertTrue(app.historyStore is RoomHistoryStore)
    }

    @Test
    fun bindHistoryStore_prunesExactlyOnce_withMaxEntriesAnd90DayCutoff() {
        val spy = PruneSpy()
        val before = System.currentTimeMillis()
        app.bindHistoryStore(spy, CoroutineScope(Dispatchers.Unconfined))
        val after = System.currentTimeMillis()
        assertEquals(1, spy.prunes.size)
        val (max, cutoff) = spy.prunes.single()
        assertEquals(HistoryRetention.MAX_ENTRIES, max)
        val ninety = HistoryRetention.MAX_AGE_DAYS * 86_400_000L
        assertTrue("cutoff=$cutoff", cutoff in (before - ninety)..(after - ninety))
    }

    @Test
    fun bindHistoryStore_exposesBoundStore_andRecorderWritingToIt() {
        val store = InMemoryHistoryStore()
        app.bindHistoryStore(store, CoroutineScope(Dispatchers.Unconfined))
        assertTrue(app.historyStore === store)
        app.historyRecorder.record(
            PageVisit("t", "https://a.com/", "A", isPrivate = false, isError = false),
        )
        kotlinx.coroutines.runBlocking { assertEquals(1, store.count()) }
    }
}
