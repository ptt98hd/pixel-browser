package app.pixel.browser.core.omnibox

import app.pixel.browser.core.omnibox.OmniboxResult.Navigate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Bảng input → [Navigate] (AC1, AC3–AC10, AC19, AC23, AC25 và edge case trim). */
@RunWith(Parameterized::class)
class OmniboxClassifierNavigateTest(
    private val ac: String,
    private val input: String,
    private val expectedUrl: String,
) {
    @Test
    fun classify_navigatesToNormalizedUrl() {
        assertEquals(
            "$ac: input='$input'",
            Navigate(expectedUrl),
            OmniboxClassifier.classify(input, SearchEngines.Google),
        )
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1} -> {2}")
        fun cases(): List<Array<String>> =
            listOf(
                arrayOf("AC1 trim", "  example.com \n", "https://example.com"),
                arrayOf("AC3 bare domain", "example.com", "https://example.com"),
                arrayOf("AC3 bare domain", "sub.example.co.uk", "https://sub.example.co.uk"),
                arrayOf(
                    "AC3 path/query/fragment kept",
                    "example.com/path?q=1#frag",
                    "https://example.com/path?q=1#frag",
                ),
                arrayOf("AC4 host lowercased, path case kept", "Example.COM/Path", "https://example.com/Path"),
                arrayOf("AC5 port", "example.com:8443/a", "https://example.com:8443/a"),
                arrayOf("AC6 localhost http", "localhost", "http://localhost"),
                arrayOf("AC6 localhost http", "localhost:8080", "http://localhost:8080"),
                arrayOf("AC6 localhost http", "LOCALHOST:8080/api", "http://localhost:8080/api"),
                arrayOf("AC7 IPv4 http", "192.168.1.1", "http://192.168.1.1"),
                arrayOf("AC7 IPv4 http", "10.0.0.2:3000/x", "http://10.0.0.2:3000/x"),
                arrayOf("AC8 IPv6 bracketed", "[::1]", "http://[::1]"),
                arrayOf("AC8 IPv6 bracketed", "[::1]:8080", "http://[::1]:8080"),
                arrayOf("AC8 IPv6 bracketed", "[2001:db8::1]/a", "http://[2001:db8::1]/a"),
                arrayOf("AC8 IPv6 unbracketed", "::1", "http://[::1]"),
                arrayOf("AC8 IPv6 unbracketed", "2001:db8::1", "http://[2001:db8::1]"),
                arrayOf("AC9 IDN punycode", "bücher.de", "https://xn--bcher-kva.de"),
                arrayOf("AC10 trailing dot", "example.com.", "https://example.com."),
                arrayOf("AC19 scheme+host normalized", "HTTPS://Example.com/A", "https://example.com/A"),
                arrayOf("AC19 http not upgraded", "http://example.com", "http://example.com"),
                arrayOf("AC23 about:blank", "about:blank", "about:blank"),
                arrayOf("AC23 about:blank", "ABOUT:BLANK", "about:blank"),
                arrayOf("AC25 host:port", "example.com:8080", "https://example.com:8080"),
                arrayOf("AC25 host:port", "localhost:8080", "http://localhost:8080"),
                arrayOf("edge zero-width trimmed", "​example.com​", "https://example.com"),
                arrayOf("edge control char trimmed", "\u0000example.com\u0007", "https://example.com"),
            )
    }
}
