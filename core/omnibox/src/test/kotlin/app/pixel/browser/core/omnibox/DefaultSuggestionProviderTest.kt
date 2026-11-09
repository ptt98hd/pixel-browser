package app.pixel.browser.core.omnibox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSuggestionProviderTest {
    private val provider = DefaultSuggestionProvider(SearchEngines.Google)

    @Test
    fun ac28_urlLikeInput_givesGoToThenSearch() =
        runTest {
            val result = provider.suggest("example.com")
            assertEquals(
                listOf(
                    SuggestionKind.GoTo to "https://example.com",
                    SuggestionKind.Search to "https://www.google.com/search?q=example.com",
                ),
                result.map { it.kind to it.url },
            )
        }

    @Test
    fun ac29_queryInput_givesSingleSearch() =
        runTest {
            val result = provider.suggest("hello world")
            assertEquals(
                listOf(SuggestionKind.Search to "https://www.google.com/search?q=hello%20world"),
                result.map { it.kind to it.url },
            )
        }

    @Test
    fun ac30_emptyInput_givesNoSuggestions() =
        runTest {
            assertEquals(emptyList<Suggestion>(), provider.suggest(""))
        }

    @Test
    fun ac30_blockedInput_givesNoSuggestions() =
        runTest {
            assertEquals(emptyList<Suggestion>(), provider.suggest("javascript:alert(1)"))
        }

    // --- Spec 014 AC9: engine động ---

    @Test
    fun ac9_dynamicEngine_isReadOnEverySuggestCall() =
        runTest {
            var current = SearchEngines.Google
            val dynamic = DefaultSuggestionProvider { current }

            assertEquals(
                listOf(
                    Suggestion(
                        SuggestionKind.Search,
                        "Search for hello world",
                        "https://www.google.com/search?q=hello%20world",
                    ),
                ),
                dynamic.suggest("hello world"),
            )

            current = SearchEngines.DuckDuckGo
            assertEquals(
                listOf(
                    Suggestion(
                        SuggestionKind.Search,
                        "Search for hello world",
                        "https://duckduckgo.com/?q=hello%20world",
                    ),
                ),
                dynamic.suggest("hello world"),
            )
        }

    @Test
    fun ac9_dynamicEngine_appliesToGoToPlusSearchSuggestions() =
        runTest {
            var current = SearchEngines.Google
            val dynamic = DefaultSuggestionProvider { current }
            current = SearchEngines.Bing
            assertEquals(
                listOf(
                    SuggestionKind.GoTo to "https://example.com",
                    SuggestionKind.Search to "https://www.bing.com/search?q=example.com",
                ),
                dynamic.suggest("example.com").map { it.kind to it.url },
            )
        }

    @Test
    fun ac9_legacyConstructor_withEngineInstance_behavesAsBefore() =
        runTest {
            val legacy = DefaultSuggestionProvider(SearchEngines.Brave)
            assertEquals(
                listOf(SuggestionKind.Search to "https://search.brave.com/search?q=kotlin"),
                legacy.suggest("kotlin").map { it.kind to it.url },
            )
        }
}
