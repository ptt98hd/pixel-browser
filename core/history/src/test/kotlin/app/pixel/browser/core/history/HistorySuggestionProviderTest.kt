package app.pixel.browser.core.history

import app.pixel.browser.core.omnibox.DefaultSuggestionProvider
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.SuggestionAggregator
import app.pixel.browser.core.omnibox.SuggestionKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Spec 012 AC12. */
class HistorySuggestionProviderTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private val store = InMemoryHistoryStore()

    private fun provider(
        s: HistoryStore = store,
        limit: Int = 4,
    ) = HistorySuggestionProvider(s, { now }, limit)

    private class CountingStore(
        val delegate: InMemoryHistoryStore,
    ) : HistoryStore by delegate {
        var suggestCalls = 0

        override suspend fun suggest(
            terms: List<String>,
            candidateLimit: Int,
        ): List<HistoryHit> {
            suggestCalls++
            return delegate.suggest(terms, candidateLimit)
        }
    }

    @Test
    fun blankInput_returnsEmpty_withoutQueryingStore() =
        runTest {
            val counting = CountingStore(store)
            store.insert("https://wiki.org/", "Wiki", now)
            assertTrue(provider(counting).suggest("").isEmpty())
            assertTrue(provider(counting).suggest("   ").isEmpty())
            assertEquals(0, counting.suggestCalls)
        }

    @Test
    fun matchesUrlOrTitle_oneSuggestionPerUrl_withLatestTitle_asHistoryKind() =
        runTest {
            store.insert("https://en.wikipedia.org/wiki/K", "Old", now - 3_000)
            store.insert("https://en.wikipedia.org/wiki/K", "Latest", now - 1_000)
            store.insert("https://a.com/", "About wiki", now - 2_000)
            store.insert("https://zzz.com/", "Nothing", now)
            val result = provider().suggest("wiki")
            assertEquals(2, result.size)
            assertTrue(result.all { it.kind == SuggestionKind.History })
            val byUrl = result.associate { it.url to it.title }
            assertEquals("Latest", byUrl["https://en.wikipedia.org/wiki/K"])
            assertEquals("About wiki", byUrl["https://a.com/"])
        }

    @Test
    fun emptyTitle_fallsBackToUrl() =
        runTest {
            store.insert("https://wiki.org/page", "", now)
            assertEquals("https://wiki.org/page", provider().suggest("wiki").single().title)
        }

    @Test
    fun schemeAndWww_doNotCountWhenMatchingUrl() =
        runTest {
            store.insert("https://www.example.com/", "Example", now)
            assertTrue("https must not match", provider().suggest("https").isEmpty())
            assertTrue("www must not match", provider().suggest("www").isEmpty())
            assertEquals(1, provider().suggest("example").size)
        }

    @Test
    fun ordersByFrecencyDescending_tiesBrokenByNewerLastVisit() =
        runTest {
            store.insert("https://wiki-a.com/", "", now - day) // 1 visit, score 100
            repeat(3) { store.insert("https://wiki-b.com/", "", now - 5 * day + it) } // score 210
            store.insert("https://wiki-c.com/", "", now) // 1 visit, score 100, newer
            assertEquals(
                listOf("https://wiki-b.com/", "https://wiki-c.com/", "https://wiki-a.com/"),
                provider().suggest("wiki").map { it.url },
            )
        }

    @Test
    fun defaultLimitIs4_customLimitHonoured() =
        runTest {
            repeat(6) { store.insert("https://wiki$it.com/", "", now - it) }
            assertEquals(4, provider().suggest("wiki").size)
            assertEquals(2, provider(limit = 2).suggest("wiki").size)
        }

    @Test
    fun multipleTerms_areAnded() =
        runTest {
            store.insert("https://a.com/wiki", "Kotlin", now)
            store.insert("https://b.com/wiki", "Java", now - 1)
            assertEquals(listOf("https://a.com/wiki"), provider().suggest("wiki kotlin").map { it.url })
        }

    @Test
    fun percentAndUnderscore_areLiteral() =
        runTest {
            store.insert("https://a.com/?p=100%", "", now)
            store.insert("https://a.com/?p=1000", "", now - 1)
            store.insert("https://b.com/a_b", "", now - 2)
            store.insert("https://b.com/axb", "", now - 3)
            assertEquals(listOf("https://a.com/?p=100%"), provider().suggest("100%").map { it.url })
            assertEquals(listOf("https://b.com/a_b"), provider().suggest("a_b").map { it.url })
        }

    @Test
    fun storeFailure_isSkippedByAggregator_keepingOtherSuggestions() =
        runTest {
            store.failWith = IOException("boom")
            val aggregator =
                SuggestionAggregator(listOf(DefaultSuggestionProvider(SearchEngines.Default), provider()))
            val result = aggregator.suggest("wiki")
            assertTrue(result.isNotEmpty())
            assertTrue(result.none { it.kind == SuggestionKind.History })
        }
}
