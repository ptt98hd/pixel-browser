package app.pixel.browser.core.history

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 012 AC2 – hợp đồng chung cho mọi [HistoryStore]; chạy trên InMemory (JVM) và Room (Robolectric). */
abstract class HistoryStoreContractTest {
    protected abstract fun createStore(): HistoryStore

    protected open fun release() {}

    private val store: HistoryStore by lazy { createStore() }

    @After
    fun tearDown() = release()

    private fun all(
        query: String = "",
        limit: Int = 100,
    ): List<HistoryEntry> = runBlocking { withTimeout(10_000) { store.entries(query, limit).first() } }

    private fun urls(
        query: String,
        limit: Int = 100,
    ) = all(query, limit).map { it.url }

    private fun add(
        url: String,
        title: String = "",
        at: Long,
    ): Long = runBlocking { store.insert(url, title, at) }

    // (a)
    @Test
    fun ac2a_entries_newestFirst_andRespectsLimit() {
        add("https://1.com/", at = 100)
        add("https://3.com/", at = 300)
        add("https://2.com/", at = 200)
        add("https://5.com/", at = 500)
        add("https://4.com/", at = 400)
        assertEquals(
            listOf("https://5.com/", "https://4.com/", "https://3.com/", "https://2.com/", "https://1.com/"),
            urls(""),
        )
        assertEquals(listOf("https://5.com/", "https://4.com/"), urls("", limit = 2))
    }

    @Test
    fun ac2a_entries_sameVisitedAt_newerIdFirst() {
        val first = add("https://a.com/", at = 100)
        val second = add("https://b.com/", at = 100)
        assertEquals(listOf(second, first), all().map { it.id })
    }

    @Test
    fun ac2a_insert_returnsDistinctIds_andStoresFields() {
        val id = add("https://a.com/x", "Title A", 42)
        assertEquals(listOf(HistoryEntry(id, "https://a.com/x", "Title A", 42)), all())
    }

    // (b)
    @Test
    fun ac2b_query_matchesUrlOrTitle_caseInsensitive() {
        add("https://google.com/", "Search", 1)
        add("https://a.com/", "Goo Docs", 2)
        add("https://b.com/", "Other", 3)
        assertEquals(listOf("https://a.com/", "https://google.com/"), urls("goo"))
        assertEquals(listOf("https://a.com/", "https://google.com/"), urls("GOO"))
    }

    @Test
    fun ac2b_query_multipleTerms_mustAllMatch() {
        add("https://google.com/", "Search", 1)
        add("https://a.com/", "Goo Docs", 2)
        assertEquals(listOf("https://a.com/"), urls("goo docs"))
        assertEquals(listOf("https://a.com/"), urls("  docs   goo  "))
        assertTrue(urls("goo nothing").isEmpty())
    }

    // (c)
    @Test
    fun ac2c_query_percentUnderscoreBackslash_areLiteral() {
        add("https://a.com/?p=100%", "", 1)
        add("https://a.com/?p=1000", "", 2)
        add("https://b.com/a_b", "", 3)
        add("https://b.com/axb", "", 4)
        add("https://c.com/a\\b", "", 5)
        assertEquals(listOf("https://a.com/?p=100%"), urls("100%"))
        assertEquals(listOf("https://b.com/a_b"), urls("a_b"))
        assertEquals(listOf("https://c.com/a\\b"), urls("\\"))
        assertTrue(urls("%").size == 1)
    }

    // (d)
    @Test
    fun ac2d_delete_removesOnlyThatRow_andFlowEmitsAgain() =
        runBlocking {
            val keep = add("https://keep.com/", at = 1)
            val gone = add("https://gone.com/", at = 2)
            val emissions = Channel<List<HistoryEntry>>(Channel.UNLIMITED)
            val job =
                launch(Dispatchers.Default, start = CoroutineStart.DEFAULT) {
                    store.entries("", 10).collect { emissions.send(it) }
                }
            withTimeout(10_000) { assertEquals(listOf(gone, keep), emissions.receive().map { it.id }) }
            store.delete(gone)
            withTimeout(10_000) {
                while (true) {
                    val list = emissions.receive()
                    if (list.map { it.id } == listOf(keep)) break
                }
            }
            job.cancel()
            assertEquals(listOf(keep), all().map { it.id })
        }

    // (e)
    @Test
    fun ac2e_deleteBetween_isClosedInterval_andReturnsCount() {
        add("https://0.com/", at = 99)
        add("https://1.com/", at = 100)
        add("https://2.com/", at = 150)
        add("https://3.com/", at = 200)
        add("https://4.com/", at = 201)
        val removed = runBlocking { store.deleteBetween(100, 200) }
        assertEquals(3, removed)
        assertEquals(listOf("https://4.com/", "https://0.com/"), urls(""))
    }

    // (f)
    @Test
    fun ac2f_touch_updatesTitleAndVisitedAt_andReportsMissingId() {
        val id = add("https://a.com/", "Old", 10)
        assertTrue(runBlocking { store.touch(id, "New", 99) })
        assertEquals(listOf(HistoryEntry(id, "https://a.com/", "New", 99)), all())
        assertFalse(runBlocking { store.touch(id + 1000, "x", 1) })
    }

    // (g)
    @Test
    fun ac2g_suggest_groupsByUrl_withLatestTitleAndCounts() {
        add("https://en.wikipedia.org/wiki/A", "Old title", 100)
        add("https://en.wikipedia.org/wiki/A", "New title", 300)
        add("https://en.wikipedia.org/wiki/A", "Mid title", 200)
        add("https://en.wikipedia.org/wiki/B", "B page", 250)
        add("https://other.com/", "Unrelated", 400)
        val hits = runBlocking { store.suggest(listOf("wiki"), 100) }.sortedBy { it.url }
        assertEquals(
            listOf(
                HistoryHit("https://en.wikipedia.org/wiki/A", "New title", 3, 300),
                HistoryHit("https://en.wikipedia.org/wiki/B", "B page", 1, 250),
            ),
            hits,
        )
    }

    @Test
    fun ac2g_suggest_multipleTermsAreAnded_andCandidateLimitApplies() {
        add("https://a.com/wiki", "Alpha", 1)
        add("https://b.com/wiki", "Beta", 2)
        add("https://c.com/wiki", "Gamma", 3)
        assertEquals(
            listOf("https://b.com/wiki"),
            runBlocking { store.suggest(listOf("wiki", "beta"), 100) }.map { it.url },
        )
        assertEquals(2, runBlocking { store.suggest(listOf("wiki"), 2) }.size)
    }

    // (h)
    @Test
    fun ac2h_prune_dropsOlderThanThenOldestUntilMax_andCountMatches() {
        add("https://old.com/", at = 10)
        add("https://a.com/", at = 100)
        add("https://b.com/", at = 200)
        add("https://c.com/", at = 300)
        val removed = runBlocking { store.prune(maxEntries = 2, olderThan = 50) }
        assertEquals(2, removed)
        assertEquals(listOf("https://c.com/", "https://b.com/"), urls(""))
        assertEquals(2, runBlocking { store.count() })
    }

    @Test
    fun ac2h_prune_underLimit_removesOnlyOldRows() {
        add("https://old.com/", at = 10)
        add("https://a.com/", at = 100)
        assertEquals(1, runBlocking { store.prune(maxEntries = 10, olderThan = 50) })
        assertEquals(1, runBlocking { store.count() })
    }

    @Test
    fun ac2h_count_isZeroWhenEmpty() {
        assertEquals(0, runBlocking { store.count() })
    }
}
