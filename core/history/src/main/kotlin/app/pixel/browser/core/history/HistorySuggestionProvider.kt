package app.pixel.browser.core.history

import app.pixel.browser.core.omnibox.Suggestion
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.core.omnibox.SuggestionProvider

class HistorySuggestionProvider(
    private val store: HistoryStore,
    private val clock: () -> Long,
    private val limit: Int = 4,
) : SuggestionProvider {
    override suspend fun suggest(input: String): List<Suggestion> {
        val terms = HistoryQuery.terms(input)
        if (terms.isEmpty()) return emptyList()
        val now = clock()
        return store
            .suggest(terms, CANDIDATES)
            .filter { hit -> matches(hit, terms) }
            .sortedWith(
                compareByDescending<HistoryHit> { Frecency.score(it.visitCount, it.lastVisitedAt, now) }
                    .thenByDescending { it.lastVisitedAt },
            ).take(limit)
            .map { Suggestion(SuggestionKind.History, it.title.ifBlank { it.url }, it.url) }
    }

    /** Khớp lại ở đây vì store khớp trên URL đầy đủ, còn gợi ý bỏ `https://` và `www.` khi so khớp URL. */
    private fun matches(
        hit: HistoryHit,
        terms: List<String>,
    ): Boolean {
        val shown = hit.url.substringAfter("://").removePrefix("www.")
        return terms.all { shown.contains(it, ignoreCase = true) || hit.title.contains(it, ignoreCase = true) }
    }

    private companion object {
        const val CANDIDATES = 100
    }
}
