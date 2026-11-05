package app.pixel.browser.core.omnibox

import app.pixel.browser.core.omnibox.OmniboxResult.Blocked
import app.pixel.browser.core.omnibox.OmniboxResult.Navigate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hồi quy cho các điểm code review và mục "Quyết định" của spec 004. */
class OmniboxReviewFixesTest {
    private val google = SearchEngines.Google

    private fun classify(input: String) = OmniboxClassifier.classify(input, google)

    private fun assertSearch(input: String) {
        val result = classify(input)
        assertTrue("input='$input' expected Search but was $result", result is OmniboxResult.Search)
    }

    @Test
    fun ipv6_unbracketedStartingWithHexLetters_navigatesHttp() {
        assertEquals(Navigate("http://[fe80::1]"), classify("fe80::1"))
        assertEquals(Navigate("http://[abcd::1]"), classify("abcd::1"))
        assertEquals(Navigate("http://[dead:beef::1]"), classify("dead:beef::1"))
    }

    @Test
    fun idn_nonRoundTrippingHost_isSearch() {
        assertSearch("faß.de")
        assertSearch("example️.com")
    }

    @Test
    fun idn_roundTrippingHost_stillNavigates() {
        assertEquals(Navigate("https://xn--bcher-kva.de"), classify("Bücher.de"))
    }

    @Test
    fun ipv4_leadingZeroOctet_isSearch() {
        assertSearch("192.168.01.1")
        assertSearch("010.0.0.1")
    }

    @Test
    fun ipv4_zeroOctet_navigates() {
        assertEquals(Navigate("http://0.0.0.0"), classify("0.0.0.0"))
    }

    @Test
    fun decision_userinfoWithExplicitScheme_isSearch() {
        assertSearch("https://google.com@evil.com")
        assertSearch("https://user:pw@example.com")
    }

    @Test
    fun decision_singleLabelHostWithExplicitScheme_navigates() {
        assertEquals(Navigate("https://intranet"), classify("https://intranet"))
    }

    @Test
    fun decision_dangerousSchemeWithPortLikeSuffix_isBlocked() {
        assertEquals(Blocked("javascript:1", BlockReason.DangerousScheme), classify("javascript:1"))
    }

    @Test
    fun decision_blockedInput_isTrimmed() {
        assertEquals("javascript:void(0)", (classify("  javascript:void(0)") as Blocked).input)
    }

    @Test
    fun decision_suggestionTitles() =
        runTest {
            val provider = DefaultSuggestionProvider(google)
            assertEquals(
                listOf("https://example.com", "Search for example.com"),
                provider.suggest("example.com").map { it.title },
            )
            assertEquals(listOf("Search for hello world"), provider.suggest("hello world").map { it.title })
        }

    @Test
    fun urlDisplay_backslashTerminatesAuthority() {
        assertEquals("evil.com", UrlDisplay.host("https://evil.com\\@google.com"))
    }

    @Test
    fun searchEngine_templateWithUserinfo_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchEngine("bad", "Bad", "https://user@s.example/?q=%s")
        }
    }

    @Test
    fun searchEngine_templateWithPlaceholderInAuthority_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchEngine("bad", "Bad", "https://%s.example/?q=%s")
        }
    }
}
