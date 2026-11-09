package app.pixel.browser.core.bookmarks

import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.core.omnibox.SuggestionProvider
import app.pixel.browser.core.omnibox.UrlDisplay

class BookmarkSuggestionProvider(
    private val repo: BookmarkRepository,
    private val limit: Int = 3,
) : SuggestionProvider {
    override suspend fun suggest(input: String): List<Suggestion> {
        val terms = input.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return repo
            .suggest(terms, CANDIDATES)
            .sortedWith(
                compareByDescending<Bookmark> { startsWithAny(it, terms) }.thenByDescending { it.createdAt },
            ).distinctBy { BookmarkRules.urlKey(it.url) }
            .take(limit)
            .map { Suggestion(SuggestionKind.Bookmark, it.title.ifBlank { it.url }, it.url) }
    }

    private fun startsWithAny(
        bookmark: Bookmark,
        terms: List<String>,
    ): Boolean {
        val host = UrlDisplay.host(bookmark.url)
        return terms.any { bookmark.title.startsWith(it, ignoreCase = true) || host.startsWith(it, ignoreCase = true) }
    }

    private companion object {
        const val CANDIDATES = 100
        val WHITESPACE = Regex("\\s+")
    }
}
