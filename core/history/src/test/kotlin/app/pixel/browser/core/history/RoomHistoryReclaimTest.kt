package app.pixel.browser.core.history

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Spec 012: sau khi xóa, dữ liệu không nằm lại trong file WAL. */
@RunWith(RobolectricTestRunner::class)
class RoomHistoryReclaimTest {
    private fun fileDb(name: String): HistoryDatabase =
        Room
            .databaseBuilder(RuntimeEnvironment.getApplication(), HistoryDatabase::class.java, name)
            .build()

    private fun walOf(name: String) = File(RuntimeEnvironment.getApplication().getDatabasePath(name).path + "-wal")

    @Test
    fun deleteBetween_all_truncatesWal_andKeepsDbUsable() =
        runBlocking {
            val db = fileDb("reclaim.db")
            val store = RoomHistoryStore(db)
            repeat(50) { store.insert("https://a.com/$it", "secret title $it", it.toLong()) }
            assertEquals(50, store.deleteBetween(0, Long.MAX_VALUE))
            assertTrue("wal=${walOf("reclaim.db").length()}", walOf("reclaim.db").length() == 0L)
            store.insert("https://b.com/", "", 1)
            assertEquals(1, store.count())
            db.close()
        }

    @Test
    fun prune_truncatesWal() =
        runBlocking {
            val db = fileDb("reclaim2.db")
            val store = RoomHistoryStore(db)
            repeat(50) { store.insert("https://a.com/$it", "t$it", it.toLong()) }
            assertTrue(store.prune(maxEntries = 10, olderThan = 0) > 0)
            assertTrue(walOf("reclaim2.db").length() == 0L)
            db.close()
        }
}
