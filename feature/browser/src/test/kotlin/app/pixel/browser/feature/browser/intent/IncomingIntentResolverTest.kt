package app.pixel.browser.feature.browser.intent

import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.searchUrl
import app.pixel.browser.feature.browser.intent.IncomingRequest.OpenUrl
import app.pixel.browser.feature.browser.intent.IncomingRequest.Rejected
import app.pixel.browser.feature.browser.intent.IncomingRequest.Resume
import app.pixel.browser.feature.browser.intent.IncomingRequest.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 006 – AC7–AC11: [IncomingIntentResolver] (JVM thuần, engine = Google). */
class IncomingIntentResolverTest {
    private val engine = SearchEngines.Google

    private fun intent(
        action: String? = null,
        data: String? = null,
        type: String? = null,
        extraText: String? = null,
        query: String? = null,
        processText: String? = null,
        categories: Set<String> = emptySet(),
        fromHistory: Boolean = false,
    ) = IncomingIntent(action, data, type, extraText, query, processText, categories, fromHistory)

    private fun resolve(i: IncomingIntent) = IncomingIntentResolver.resolve(i, engine)

    private fun view(data: String?) = resolve(intent(action = VIEW, data = data))

    // --- AC7 ---

    @Test
    fun ac7_viewHttps_opensUrlUnchanged() {
        assertEquals(OpenUrl("https://example.com/a?b=1"), view("https://example.com/a?b=1"))
    }

    @Test
    fun ac7_viewUppercaseHttp_isNormalizedByOmniboxClassifier() {
        assertEquals(OpenUrl("http://example.com"), view("HTTP://Example.com"))
    }

    // --- AC8 ---

    @Test
    fun ac8_viewNonWebSchemes_areRejectedAsUnsupportedScheme() {
        val urls =
            listOf(
                "javascript:alert(1)",
                "file:///data/data/app.pixel.browser/x",
                "content://com.android.contacts/1",
                "intent://x#Intent;end",
                "data:text/html,x",
                "about:blank",
                "ftp://a.com",
                "market://details?id=x",
                "JavaScript:alert(1)",
            )
        for (url in urls) assertEquals(url, Rejected(RejectReason.UnsupportedScheme), view(url))
    }

    @Test
    fun ac8_viewMalformedWebUrls_areRejectedAsInvalidUrl_neverSearch() {
        for (url in listOf("https://", "https://user@evil.com", "https://exa mple.com")) {
            assertEquals(url, Rejected(RejectReason.InvalidUrl), view(url))
        }
    }

    @Test
    fun ac8_viewWithoutData_isRejectedAsEmpty() {
        assertEquals(Rejected(RejectReason.Empty), view(null))
    }

    // --- AC9 ---

    private fun searchIntents(text: String?): Map<String, IncomingIntent> =
        mapOf(
            WEB_SEARCH to intent(action = WEB_SEARCH, query = text),
            SEARCH to intent(action = SEARCH, query = text),
            PROCESS_TEXT to intent(action = PROCESS_TEXT, type = "text/plain", processText = text),
        )

    private fun assertAllSearchActions(
        text: String?,
        expected: IncomingRequest,
    ) {
        for ((action, i) in searchIntents(text)) assertEquals("$action: '$text'", expected, resolve(i))
    }

    @Test
    fun ac9_searchActions_plainText_searchesWithEngine() {
        assertAllSearchActions(
            "hello world",
            Search("hello world", "https://www.google.com/search?q=hello%20world"),
        )
    }

    @Test
    fun ac9_searchActions_hostLikeText_opensUrl() {
        assertAllSearchActions("example.com", OpenUrl("https://example.com"))
    }

    @Test
    fun ac9_searchActions_dangerousScheme_isRejected() {
        assertAllSearchActions("javascript:alert(1)", Rejected(RejectReason.UnsupportedScheme))
    }

    @Test
    fun ac9_searchActions_emptyNullOrBlank_isRejectedAsEmpty() {
        for (text in listOf("", null, "  ")) assertAllSearchActions(text, Rejected(RejectReason.Empty))
    }

    @Test
    fun ac9_searchActions_longQuery_isTruncatedToMaxLength() {
        val long = "a".repeat(3000)
        val expected = "a".repeat(IncomingIntentResolver.MAX_QUERY_LENGTH)
        assertEquals(2048, IncomingIntentResolver.MAX_QUERY_LENGTH)
        assertAllSearchActions(long, Search(expected, engine.searchUrl(expected)))
    }

    @Test
    fun ac9_searchActions_truncation_doesNotSplitSurrogatePair() {
        // Ký tự 2048–2049 là cặp surrogate của emoji; cắt ở 2048 sẽ để lại nửa cặp.
        val text = "a".repeat(2047) + "😀" + "b".repeat(100)
        val expected = "a".repeat(2047)
        for ((action, i) in searchIntents(text)) {
            val r = resolve(i)
            assertTrue("$action: $r", r is Search)
            r as Search
            assertTrue("$action: length ${r.query.length}", r.query.length <= IncomingIntentResolver.MAX_QUERY_LENGTH)
            assertTrue("$action: dangling surrogate", !r.query.last().isHighSurrogate())
            assertEquals(action, expected, r.query)
            assertEquals(action, engine.searchUrl(expected), r.url)
        }
    }

    // --- AC10 ---

    private fun send(
        text: String?,
        type: String? = "text/plain",
    ) = resolve(intent(action = SEND, type = type, extraText = text))

    @Test
    fun ac10_sendUrl_opensUrl() {
        assertEquals(OpenUrl("https://example.com/a"), send("https://example.com/a"))
    }

    @Test
    fun ac10_sendTextContainingUrl_opensFirstUrl_withoutTrailingPunctuation() {
        assertEquals(OpenUrl("https://example.com/a"), send("Xem cái này https://example.com/a nhé"))
        assertEquals(OpenUrl("https://example.com/a"), send("Link: https://example.com/a."))
        assertEquals(OpenUrl("https://example.com/a"), send("(https://example.com/a)"))
        assertEquals(OpenUrl("https://example.com/a"), send("x https://example.com/a!? y http://b.com"))
    }

    @Test
    fun ac10_sendPlainText_searches() {
        assertEquals(Search("phở bò", engine.searchUrl("phở bò")), send("phở bò"))
    }

    @Test
    fun ac10_sendDangerousScheme_isRejected() {
        assertEquals(Rejected(RejectReason.UnsupportedScheme), send("javascript:alert(1)"))
    }

    @Test
    fun ac10_sendWithoutText_isRejectedAsEmpty() {
        assertEquals(Rejected(RejectReason.Empty), send(null))
    }

    @Test
    fun ac10_sendNonTextType_isRejectedAsEmpty() {
        assertEquals(Rejected(RejectReason.Empty), send("https://example.com/a", type = "image/png"))
    }

    // --- AC11 ---

    @Test
    fun ac11_launcherIntent_resumes() {
        assertEquals(Resume, resolve(intent(action = MAIN, categories = setOf(LAUNCHER))))
    }

    @Test
    fun ac11_nullAction_resumes() {
        assertEquals(Resume, resolve(intent(action = null, data = "https://example.com")))
    }

    @Test
    fun ac11_unsupportedAction_resumes() {
        assertEquals(Resume, resolve(intent(action = "android.intent.action.EDIT", data = "https://example.com")))
    }

    @Test
    fun ac11_launchedFromHistory_alwaysResumes() {
        val intents =
            listOf(
                intent(action = VIEW, data = "https://example.com", fromHistory = true),
                intent(action = WEB_SEARCH, query = "hello", fromHistory = true),
                intent(action = SEND, type = "text/plain", extraText = "https://a.com", fromHistory = true),
                intent(action = PROCESS_TEXT, type = "text/plain", processText = "x", fromHistory = true),
                intent(action = VIEW, data = "javascript:alert(1)", fromHistory = true),
            )
        for (i in intents) assertEquals(i.toString(), Resume, resolve(i))
    }

    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val MAIN = "android.intent.action.MAIN"
        const val SEND = "android.intent.action.SEND"
        const val SEARCH = "android.intent.action.SEARCH"
        const val WEB_SEARCH = "android.intent.action.WEB_SEARCH"
        const val PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
        const val LAUNCHER = "android.intent.category.LAUNCHER"
    }
}
