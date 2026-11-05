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
}
