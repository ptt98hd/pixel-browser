package app.pixel.browser.core.bookmarks

import androidx.room.Room
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomBookmarkRepositoryContractTest : BookmarkRepositoryContractTest() {
    private var db: BookmarkDatabase? = null

    override fun createRepo(
        maxNodes: Int,
        clock: () -> Long,
    ): BookmarkRepository {
        val database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), BookmarkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        db = database
        return RoomBookmarkRepository(database, maxNodes, clock)
    }

    override fun release() {
        db?.close()
    }
}
