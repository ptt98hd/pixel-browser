package app.pixel.browser.core.bookmarks

import app.pixel.browser.core.omnibox.DefaultSuggestionProvider
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionAggregator
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.core.omnibox.SuggestionProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Spec 013 AC10. */
class BookmarkSuggestionProviderTest {
    private var now = 1_000L
    private val repo = InMemoryBookmarkRepository(clock = { now++ })

    private class CountingRepo(
        val delegate: InMemoryBookmarkRepository,
    ) : BookmarkRepository by delegate {
        var suggestCalls = 0

        override suspend fun suggest(
            terms: List<String>,
            limit: Int,
        ): List<Bookmark> {
            suggestCalls++
            return delegate.suggest(terms, limit)
        }
    }

    private suspend fun add(
        url: String,
        title: String,
    ) = repo.addBookmark(url, title, ROOT_ID)

    @Test
    fun blankInput_returnsEmpty_withoutQueryingRepo() =
        runTest {
            val counting = CountingRepo(repo)
            add("https://wiki.org/", "Wiki")
            assertTrue(BookmarkSuggestionProvider(counting).suggest("").isEmpty())
            assertTrue(BookmarkSuggestionProvider(counting).suggest("   ").isEmpty())
            assertEquals(0, counting.suggestCalls)
        }

    @Test
    fun matches_areBookmarkKind_withTitleAndFullUrl() =
        runTest {
            add("https://en.wikipedia.org/wiki/K", "Kotlin")
            add("https://zzz.com/", "Nothing")
            val result = BookmarkSuggestionProvider(repo).suggest("wiki")
            assertEquals(
                listOf(Suggestion(SuggestionKind.Bookmark, "Kotlin", "https://en.wikipedia.org/wiki/K")),
                result,
            )
        }

    @Test
    fun blankTitle_fallsBackToUrl() =
        runTest {
            val blankTitled =
                object : BookmarkRepository by repo {
                    override suspend fun suggest(
                        terms: List<String>,
                        limit: Int,
                    ) = listOf(Bookmark(1, ROOT_ID, "", "https://wiki.org/p", 0, 1, 1))
                }
            assertEquals("https://wiki.org/p", BookmarkSuggestionProvider(blankTitled).suggest("wiki").single().title)
        }

    @Test
    fun defaultLimitIs3_customLimitHonoured() =
        runTest {
            repeat(6) { add("https://wiki$it.com/", "t$it") }
            assertEquals(3, BookmarkSuggestionProvider(repo).suggest("wiki").size)
            assertEquals(5, BookmarkSuggestionProvider(repo, limit = 5).suggest("wiki").size)
        }

    @Test
    fun sameUrlBookmarkedTwice_isOneSuggestion() =
        runTest {
            add("https://wiki.org/", "one")
            add("https://wiki.org/", "two")
            assertEquals(1, BookmarkSuggestionProvider(repo).suggest("wiki").size)
        }

    @Test
    fun ranking_prefixOfTitleOrHostFirst_thenNewestCreated() =
        runTest {
            add("https://a.com/x", "Titled wiki-less then prefix wiki") // chứa, không bắt đầu bằng
            add("https://b.com/x", "wiki home") // title bắt đầu bằng
            add("https://wiki.org/x", "Other") // host bắt đầu bằng
            add("https://c.com/wiki", "Newest contains wiki") // mới nhất nhưng chỉ chứa
            val titles = BookmarkSuggestionProvider(repo, limit = 10).suggest("wiki").map { it.title }
            assertEquals(
                listOf("Other", "wiki home", "Newest contains wiki", "Titled wiki-less then prefix wiki"),
                titles,
            )
        }

    @Test
    fun repoFailure_isSkippedByAggregator_keepingOtherSuggestions() =
        runTest {
            repo.failWith = IOException("boom")
            val aggregator =
                SuggestionAggregator(
                    listOf(DefaultSuggestionProvider(SearchEngines.Default), BookmarkSuggestionProvider(repo)),
                )
            val result = aggregator.suggest("wiki")
            assertTrue(result.isNotEmpty())
            assertTrue(result.none { it.kind == SuggestionKind.Bookmark })
        }

    @Test
    fun aggregator_putsBookmarkBeforeHistory_andDedupesUrlKeepingBookmark() =
        runTest {
            add("https://wiki.org/", "Bookmarked wiki")
            val history =
                SuggestionProvider {
                    listOf(
                        Suggestion(SuggestionKind.History, "History wiki", "https://wiki.org/"),
                        Suggestion(SuggestionKind.History, "Other history", "https://other-wiki.org/"),
                    )
                }
            val aggregator =
                SuggestionAggregator(
                    listOf(
                        DefaultSuggestionProvider(SearchEngines.Default),
                        BookmarkSuggestionProvider(repo),
                        history,
                    ),
                    limit = 8,
                )
            val result = aggregator.suggest("wiki")
            val kinds = result.map { it.kind }
            assertTrue(kinds.indexOf(SuggestionKind.Bookmark) < kinds.indexOf(SuggestionKind.History))
            assertEquals(1, result.count { it.url == "https://wiki.org/" })
            assertEquals(SuggestionKind.Bookmark, result.first { it.url == "https://wiki.org/" }.kind)
        }
}
