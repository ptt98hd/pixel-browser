package app.pixel.browser.core.history

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Spec 012 AC3–AC5 – chính sách ghi, dedupe liên tiếp, độ bền. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRecorderTest {
    private val store = InMemoryHistoryStore()
    private var now = 1_000L

    private fun TestScope.recorder() = HistoryRecorder(store, this) { now }

    private fun visit(
        url: String,
        title: String = "T",
        tab: String = "t1",
        isPrivate: Boolean = false,
        isError: Boolean = false,
    ) = PageVisit(tab, url, title, isPrivate, isError)

    private suspend fun rows() = store.entries("", 1000).first()

    // --- AC3 ---

    @Test
    fun ac3_record_writesOneRow_withUrlTitleAndClockTime() =
        runTest {
            recorder().record(visit("https://a.com/x", "A"))
            advanceUntilIdle()
            val row = rows().single()
            assertEquals("https://a.com/x", row.url)
            assertEquals("A", row.title)
            assertEquals(1_000L, row.visitedAt)
        }

    @Test
    fun ac3_privateTab_isNeverRecorded() =
        runTest {
            recorder().record(visit("https://a.com/", isPrivate = true))
            advanceUntilIdle()
            assertTrue(rows().isEmpty())
        }

    @Test
    fun ac3_errorPage_isNeverRecorded() =
        runTest {
            recorder().record(visit("https://a.com/", isError = true))
            advanceUntilIdle()
            assertTrue(rows().isEmpty())
        }

    @Test
    fun ac3_nonWebSchemes_areNeverRecorded() =
        runTest {
            val r = recorder()
            listOf(
                "about:blank",
                "pixel://home",
                "file:///sdcard/a.html",
                "javascript:alert(1)",
                "data:text/html,hi",
                "blob:https://a.com/uuid",
                "content://media/1",
                "ftp://a.com/",
            ).forEach { r.record(visit(it)) }
            advanceUntilIdle()
            assertTrue("rows=${rows()}", rows().isEmpty())
        }

    @Test
    fun ac3_urlWithUserInfo_isNeverRecorded() =
        runTest {
            recorder().record(visit("https://u:p@a.com/"))
            recorder().record(visit("https://user@a.com/", tab = "t2"))
            advanceUntilIdle()
            assertTrue(rows().isEmpty())
        }

    @Test
    fun ac3_urlLongerThanMax_isNeverRecorded_butExactlyMaxIs() =
        runTest {
            val base = "https://a.com/"
            val ok = base + "x".repeat(HistoryRetention.MAX_URL - base.length)
            val tooLong = ok + "y"
            val r = recorder()
            r.record(visit(tooLong, tab = "t1"))
            r.record(visit(ok, tab = "t2"))
            advanceUntilIdle()
            assertEquals(listOf(ok), rows().map { it.url })
        }

    @Test
    fun ac3_blankTitle_fallsBackToHost() =
        runTest {
            val r = recorder()
            r.record(visit("https://www.example.com/p", title = "", tab = "t1"))
            r.record(visit("https://b.org/", title = "   ", tab = "t2"))
            advanceUntilIdle()
            assertEquals(setOf("example.com", "b.org"), rows().map { it.title }.toSet())
        }

    @Test
    fun ac3_longTitle_isCutTo200() =
        runTest {
            recorder().record(visit("https://a.com/", title = "x".repeat(500)))
            advanceUntilIdle()
            assertEquals(HistoryRetention.MAX_TITLE, rows().single().title.length)
        }

    @Test
    fun ac3_fragment_isKeptInRecordedUrl() =
        runTest {
            recorder().record(visit("https://a.com/p#section"))
            advanceUntilIdle()
            assertEquals("https://a.com/p#section", rows().single().url)
        }

    // --- AC4 ---

    @Test
    fun ac4_sameUrlIgnoringFragment_sameTab_updatesRowInsteadOfInserting() =
        runTest {
            val r = recorder()
            r.record(visit("https://a.com/p", title = "First"))
            advanceUntilIdle()
            now = 2_000L
            r.record(visit("https://a.com/p#top", title = "Second"))
            advanceUntilIdle()
            val row = rows().single()
            assertEquals("Second", row.title)
            assertEquals(2_000L, row.visitedAt)
        }

    @Test
    fun ac4_dedupeTouch_withBlankTitle_keepsExistingTitle_butUpdatesTime() =
        runTest {
            val r = recorder()
            r.record(visit("https://a.com/p", title = "Good title"))
            advanceUntilIdle()
            now = 2_000L
            r.record(visit("https://a.com/p#x", title = ""))
            advanceUntilIdle()
            val row = rows().single()
            assertEquals("Good title", row.title)
            assertEquals(2_000L, row.visitedAt)
        }

    @Test
    fun ac4_nonConsecutiveRepeat_createsThreeRows() =
        runTest {
            val r = recorder()
            listOf("https://a.com/", "https://b.com/", "https://a.com/").forEach {
                r.record(visit(it))
                now += 10
                advanceUntilIdle()
            }
            assertEquals(3, rows().size)
        }

    @Test
    fun ac4_sameUrlInTwoTabs_createsTwoRows() =
        runTest {
            val r = recorder()
            r.record(visit("https://a.com/", tab = "t1"))
            r.record(visit("https://a.com/", tab = "t2"))
            advanceUntilIdle()
            assertEquals(2, rows().size)
        }

    @Test
    fun ac4_forgetTab_thenSameUrl_createsNewRow() =
        runTest {
            val r = recorder()
            r.record(visit("https://a.com/", tab = "t1"))
            advanceUntilIdle()
            r.forgetTab("t1")
            r.record(visit("https://a.com/", tab = "t1"))
            advanceUntilIdle()
            assertEquals(2, rows().size)
        }

    @Test
    fun ac4_record_doesNotBlockCaller_rowAppearsOnlyAfterScopeRuns() =
        runTest {
            recorder().record(visit("https://a.com/"))
            assertTrue("must not write synchronously", rows().isEmpty())
            advanceUntilIdle()
            assertEquals(1, rows().size)
        }

    // --- AC5 ---

    @Test
    fun ac5_storeFailure_doesNotThrow_andLaterWritesStillWork() =
        runTest {
            val r = recorder()
            store.failWith = IOException("disk full")
            r.record(visit("https://a.com/", tab = "t1"))
            advanceUntilIdle()
            store.failWith = null
            r.record(visit("https://b.com/", tab = "t1"))
            advanceUntilIdle()
            assertEquals(listOf("https://b.com/"), rows().map { it.url })
        }

    @Test
    fun ac5_privateVisit_doesNotChangeDedupeStateOfNormalTab() =
        runTest {
            val r = recorder()
            r.record(visit("https://a.com/", tab = "t1", isPrivate = true))
            r.record(visit("https://a.com/", tab = "t1", isPrivate = false))
            advanceUntilIdle()
            assertEquals(1, rows().size)
        }
}
