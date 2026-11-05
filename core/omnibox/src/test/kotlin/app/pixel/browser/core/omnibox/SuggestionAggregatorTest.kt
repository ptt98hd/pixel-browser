package app.pixel.browser.core.omnibox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class SuggestionAggregatorTest {
    private val default = DefaultSuggestionProvider(SearchEngines.Google)

    /** 10 gợi ý; phần tử đầu trùng url với gợi ý GoTo của `example.com`. */
    private val fakeTen =
        SuggestionProvider {
            listOf(Suggestion(SuggestionKind.Search, "dup", "https://example.com")) +
                (1..9).map { Suggestion(SuggestionKind.Search, "fake$it", "https://fake.example/$it") }
        }

    @Test
    fun ac31_keepsProviderOrder_dedupesByUrlKeepingFirst_andCapsAtDefaultLimit8() =
        runTest {
            val result = SuggestionAggregator(listOf(default, fakeTen)).suggest("example.com")

            val expectedUrls =
                listOf(
                    "https://example.com",
                    "https://www.google.com/search?q=example.com",
                ) + (1..6).map { "https://fake.example/$it" }
            assertEquals(expectedUrls, result.map { it.url })
            assertEquals("duplicate keeps the first (GoTo) entry", SuggestionKind.GoTo, result.first().kind)
        }

    @Test
    fun ac31_respectsCustomLimit() =
        runTest {
            val result = SuggestionAggregator(listOf(default, fakeTen), limit = 3).suggest("example.com")
            assertEquals(3, result.size)
        }

    @Test
    fun ac32_failingProvider_isSkipped() =
        runTest {
            val failing = SuggestionProvider { throw IllegalStateException("boom") }
            val result = SuggestionAggregator(listOf(failing, default)).suggest("hello world")
            assertEquals(
                listOf("https://www.google.com/search?q=hello%20world"),
                result.map { it.url },
            )
        }

    @Test
    fun ac32_cancellationException_isRethrown() =
        runTest {
            val cancelling = SuggestionProvider { throw CancellationException("cancelled") }
            try {
                SuggestionAggregator(listOf(default, cancelling)).suggest("hello world")
                fail("CancellationException must propagate")
            } catch (expected: CancellationException) {
                assertEquals("cancelled", expected.message)
            }
        }
}
