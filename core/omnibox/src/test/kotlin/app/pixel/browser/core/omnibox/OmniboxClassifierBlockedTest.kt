package app.pixel.browser.core.omnibox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Bảng input → [OmniboxResult.Blocked] (AC21–AC23, edge case thứ tự kiểm tra). */
@RunWith(Parameterized::class)
class OmniboxClassifierBlockedTest(
    private val ac: String,
    private val input: String,
    private val reason: BlockReason,
) {
    @Test
    fun classify_blocksScheme() {
        val result = OmniboxClassifier.classify(input, SearchEngines.Google)
        assertTrue("$ac: input='$input' expected Blocked but was $result", result is OmniboxResult.Blocked)
        assertEquals("$ac: reason", reason, (result as OmniboxResult.Blocked).reason)
    }

    @Test
    fun classify_neverNavigatesOrSearches_withAnyEngine() {
        SearchEngines.all.forEach { engine ->
            val result = OmniboxClassifier.classify(input, engine)
            assertTrue(
                "$ac: input='$input' with ${engine.id} must not be Navigate/Search but was $result",
                result !is OmniboxResult.Navigate && result !is OmniboxResult.Search,
            )
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}: {1}")
        fun cases(): List<Array<Any>> =
            listOf(
                arrayOf("AC21 javascript", "javascript:alert(1)", BlockReason.DangerousScheme),
                arrayOf("AC21 javascript mixed case", "JavaScript:alert(1)", BlockReason.DangerousScheme),
                arrayOf("AC21 javascript leading spaces", "  javascript:void(0)", BlockReason.DangerousScheme),
                arrayOf("AC21 file", "file:///sdcard/a.html", BlockReason.DangerousScheme),
                arrayOf("AC21 content", "content://com.android.contacts/x", BlockReason.DangerousScheme),
                arrayOf("AC21 intent", "intent://scan#Intent;scheme=zxing;end", BlockReason.DangerousScheme),
                arrayOf("AC22 data", "data:text/html,<script>alert(1)</script>", BlockReason.DangerousScheme),
                arrayOf("AC23 about:config", "about:config", BlockReason.UnsupportedScheme),
                arrayOf("AC23 other about:", "about:settings", BlockReason.UnsupportedScheme),
                arrayOf("edge blocked before whitespace rule", "javascript:alert('a b')", BlockReason.DangerousScheme),
            )
    }
}
