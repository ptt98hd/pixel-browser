package app.pixel.browser.core.omnibox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Bảng input → [OmniboxResult.Search] với query = input đã trim (AC11, AC20, AC24, AC25, edge cases). */
@RunWith(Parameterized::class)
class OmniboxClassifierSearchTest(
    private val ac: String,
    private val input: String,
) {
    @Test
    fun classify_searchesTrimmedInput() {
        val result = OmniboxClassifier.classify(input, SearchEngines.Google)
        assertTrue("$ac: input='$input' expected Search but was $result", result is OmniboxResult.Search)
        assertEquals("$ac: query", input.trim(), (result as OmniboxResult.Search).query)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1}")
        fun cases(): List<Array<String>> =
            listOf(
                arrayOf("AC11 single word", "hello"),
                arrayOf("AC11 single word no dot", "example"),
                arrayOf("AC11 inner whitespace", "hello world"),
                arrayOf("AC11 inner whitespace", "example.com is down"),
                arrayOf("AC11 numeric TLD", "3.14"),
                arrayOf("AC11 IPv4 missing octet", "1.2.3"),
                arrayOf("AC11 octet > 255", "256.1.1.1"),
                arrayOf("AC11 1-char TLD", "example.c"),
                arrayOf("AC11 label starts/ends with -", "-bad-.com"),
                arrayOf("AC11 port 0", "example.com:0"),
                arrayOf("AC11 port 65536", "example.com:65536"),
                arrayOf("AC11 port not numeric", "example.com:abc"),
                arrayOf("AC11 userinfo @", "user@example.com"),
                arrayOf("AC11 number", "1234"),
                arrayOf("AC11 question", "what is 2+2?"),
                arrayOf("AC20 https without host", "https://"),
                arrayOf("AC20 http without host", "http:///path"),
                arrayOf("AC24 unknown scheme", "foo:bar"),
                arrayOf("AC24 mailto", "mailto:a@b.c"),
                arrayOf("AC24 tel", "tel:123"),
                arrayOf("AC25 word:port not localhost", "foo:8080"),
                arrayOf("edge whitespace with scheme", "https://exa mple.com"),
                arrayOf("edge only slash", "/"),
                arrayOf("edge only dot", "."),
                arrayOf("edge only colon", ":"),
                arrayOf("edge only question mark", "?"),
                arrayOf("edge zero-width inside host", "exa​mple.com"),
                arrayOf("edge tab inside javascript scheme", "java\tscript:alert(1)"),
                arrayOf("edge newline inside javascript scheme", "java\nscript:alert(1)"),
            )
    }
}
