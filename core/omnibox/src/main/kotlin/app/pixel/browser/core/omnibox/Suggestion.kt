package app.pixel.browser.core.omnibox

import kotlin.coroutines.cancellation.CancellationException

data class Suggestion(
    val kind: SuggestionKind,
    val title: String,
    val url: String,
)

enum class SuggestionKind { GoTo, Search }

fun interface SuggestionProvider {
    suspend fun suggest(input: String): List<Suggestion>
}

/** Gợi ý chỉ dựa trên [OmniboxClassifier] và search engine (spec 004). */
class DefaultSuggestionProvider(
    private val engine: SearchEngine,
) : SuggestionProvider {
    override suspend fun suggest(input: String): List<Suggestion> =
        when (val result = OmniboxClassifier.classify(input, engine)) {
            is OmniboxResult.Navigate -> {
                val query = OmniboxClassifier.normalize(input)
                listOf(
                    Suggestion(SuggestionKind.GoTo, result.url, result.url),
                    Suggestion(SuggestionKind.Search, "Search for $query", engine.searchUrl(query)),
                )
            }

            is OmniboxResult.Search -> {
                listOf(Suggestion(SuggestionKind.Search, "Search for ${result.query}", result.url))
            }

            is OmniboxResult.Blocked, OmniboxResult.Empty -> {
                emptyList()
            }
        }
}

/** Gộp gợi ý từ nhiều provider: giữ thứ tự, bỏ trùng theo url, cắt còn [limit] (spec 004). */
class SuggestionAggregator(
    private val providers: List<SuggestionProvider>,
    private val limit: Int = 8,
) : SuggestionProvider {
    init {
        require(limit >= 0) { "limit must be >= 0: $limit" }
    }

    override suspend fun suggest(input: String): List<Suggestion> {
        val byUrl = LinkedHashMap<String, Suggestion>()
        for (provider in providers) {
            if (byUrl.size >= limit) break
            val suggestions =
                try {
                    provider.suggest(input)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            suggestions.forEach { byUrl.putIfAbsent(it.url, it) }
        }
        return byUrl.values.take(limit)
    }
}
