package app.pixel.browser.feature.browser.intent

import app.pixel.browser.core.omnibox.SearchEngines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 006 review: giới hạn độ dài URL/văn bản từ intent (JVM). */
class IncomingIntentLimitsTest {
    private val engine = SearchEngines.Google
    private val max = IncomingIntentResolver.MAX_URL_LENGTH

    private fun view(data: String) =
        IncomingIntentResolver.resolve(
            IncomingIntent(VIEW, data, null, null, null, null, emptySet(), false),
            engine,
        )

    private fun send(text: String) =
        IncomingIntentResolver.resolve(
            IncomingIntent(SEND, null, "text/plain", text, null, null, emptySet(), false),
            engine,
        )

    private fun url(length: Int): String {
        val prefix = "https://example.com/"
        return prefix + "a".repeat(length - prefix.length)
    }

    @Test
    fun maxUrlLength_is8KiB() {
        assertEquals(8192, max)
    }

    @Test
    fun view_urlAtLimit_opens() {
        assertEquals(IncomingRequest.OpenUrl(url(max)), view(url(max)))
    }

    @Test
    fun view_urlOverLimit_isRejectedAsInvalidUrl() {
        assertEquals(IncomingRequest.Rejected(RejectReason.InvalidUrl), view(url(max + 1)))
    }

    @Test
    fun send_urlOverLimit_isRejectedAsInvalidUrl_notTruncatedIntoAnotherUrl() {
        assertEquals(IncomingRequest.Rejected(RejectReason.InvalidUrl), send("xem " + url(max + 500)))
    }

    @Test
    fun send_hugeTextWithShortUrl_opensUrl() {
        val text = "https://example.com/a " + "b ".repeat(200_000)
        assertEquals(IncomingRequest.OpenUrl("https://example.com/a"), send(text))
    }

    @Test
    fun send_hugePlainText_searchesTruncatedQuery() {
        val r = send("c".repeat(500_000))
        assertTrue(r.toString(), r is IncomingRequest.Search)
        assertTrue((r as IncomingRequest.Search).query.length <= IncomingIntentResolver.MAX_QUERY_LENGTH)
    }

    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val SEND = "android.intent.action.SEND"
    }
}
