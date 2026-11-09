package app.pixel.browser.core.history

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 012 AC1 – spike Room + KSP: insert một dòng qua DAO rồi đọc lại qua `entries("", 10)`. */
@RunWith(RobolectricTestRunner::class)
class RoomHistorySpikeTest {
    private lateinit var db: HistoryDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HistoryDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun ac1_roomWithKsp_insertedRowIsReturnedByEntries() =
        runBlocking {
            val id = db.visitDao().insert(VisitEntity(url = "https://a.com/", title = "A", visitedAt = 1_000L))
            val rows = RoomHistoryStore(db).entries("", 10).first()
            assertEquals(listOf(HistoryEntry(id, "https://a.com/", "A", 1_000L)), rows)
        }
}
