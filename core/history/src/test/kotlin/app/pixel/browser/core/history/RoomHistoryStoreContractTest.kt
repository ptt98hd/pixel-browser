package app.pixel.browser.core.history

import androidx.room.Room
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomHistoryStoreContractTest : HistoryStoreContractTest() {
    private var db: HistoryDatabase? = null

    override fun createStore(): HistoryStore {
        val database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HistoryDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        db = database
        return RoomHistoryStore(database)
    }

    override fun release() {
        db?.close()
    }
}
