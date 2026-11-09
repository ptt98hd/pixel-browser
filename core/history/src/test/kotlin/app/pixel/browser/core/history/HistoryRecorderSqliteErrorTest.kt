package app.pixel.browser.core.history

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 012 AC5 – `SQLiteException` từ store không làm sập `record`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HistoryRecorderSqliteErrorTest {
    @Test
    fun ac5_sqliteException_doesNotThrow_andLaterWritesStillWork() =
        runTest {
            val store = InMemoryHistoryStore()
            val r = HistoryRecorder(store, this) { 5L }
            store.failWith = SQLiteException("locked")
            r.record(PageVisit("t", "https://a.com/", "A", isPrivate = false, isError = false))
            advanceUntilIdle()
            store.failWith = null
            r.record(PageVisit("t", "https://b.com/", "B", isPrivate = false, isError = false))
            advanceUntilIdle()
            assertEquals(listOf("https://b.com/"), store.entries("", 10).first().map { it.url })
        }
}
