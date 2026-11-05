package app.pixel.browser.feature.browser.intent

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Bundle
import android.provider.Browser
import android.text.SpannableString
import android.text.style.StyleSpan
import app.pixel.browser.core.omnibox.SearchEngines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 006 – AC12: [IncomingIntent.Companion.from] đọc Intent an toàn (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class IncomingIntentTest {
    @Test
    fun ac12_view_ignoresHeadersPostDataReferrerAppIdSelectorAndComponent() {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://a.com")).apply {
                putExtra(Browser.EXTRA_HEADERS, Bundle().apply { putString("Cookie", "x") })
                putExtra("com.android.browser.post_data", "a=1".toByteArray())
                putExtra(Intent.EXTRA_REFERRER, Uri.parse("https://evil.com"))
                putExtra(Browser.EXTRA_APPLICATION_ID, "com.evil")
                putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
                selector = Intent(Intent.ACTION_MAIN).setPackage("com.evil")
                component = ComponentName("com.evil", "com.evil.Exported")
            }

        val incoming = IncomingIntent.from(intent)

        assertEquals(
            IncomingIntent(
                action = Intent.ACTION_VIEW,
                dataString = "https://a.com",
                type = null,
                extraText = null,
                query = null,
                processText = null,
                categories = emptySet(),
                launchedFromHistory = false,
            ),
            incoming,
        )
        assertEquals(
            IncomingRequest.OpenUrl("https://a.com"),
            IncomingIntentResolver.resolve(incoming, SearchEngines.Google),
        )
    }

    @Test
    fun ac12_readsAllowedFields() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_DEFAULT)
                putExtra(Intent.EXTRA_TEXT, "hello")
                putExtra(SearchManager.QUERY, "q")
                putExtra(Intent.EXTRA_PROCESS_TEXT, "p")
                addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
            }
        assertEquals(
            IncomingIntent(
                action = Intent.ACTION_SEND,
                dataString = null,
                type = "text/plain",
                extraText = "hello",
                query = "q",
                processText = "p",
                categories = setOf(Intent.CATEGORY_DEFAULT),
                launchedFromHistory = true,
            ),
            IncomingIntent.from(intent),
        )
    }

    @Test
    fun ac12_extraTextOfWrongType_isNull_withoutThrowing() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, 42)
            }
        assertNull(IncomingIntent.from(intent).extraText)
    }

    @Test
    fun ac12_processTextSpannable_isReadAsPlainString() {
        val spannable = SpannableString("phở bò").apply { setSpan(StyleSpan(1), 0, 3, 0) }
        val intent =
            Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, spannable)
            }
        val processText = IncomingIntent.from(intent).processText
        assertEquals("phở bò", processText)
        assertEquals(String::class.java, processText!!::class.java)
    }

    @Test
    fun ac12_extrasThrowingOnRead_yieldNullExtras_withoutCrash() {
        for (error in listOf(BadParcelableException("bad parcel"), RuntimeException("boom"))) {
            val intent = ThrowingExtrasIntent(error)
            val incoming = IncomingIntent.from(intent)
            assertEquals(Intent.ACTION_SEND, incoming.action)
            assertEquals("text/plain", incoming.type)
            assertNull(incoming.extraText)
            assertNull(incoming.query)
            assertNull(incoming.processText)
        }
    }

    /** Intent mà mọi cách đọc extra đều ném [error] (mô phỏng Parcel hỏng/độc hại). */
    private class ThrowingExtrasIntent(
        private val error: RuntimeException,
    ) : Intent(ACTION_SEND) {
        init {
            type = "text/plain"
        }

        override fun getStringExtra(name: String?): String? = throw error

        override fun getCharSequenceExtra(name: String?): CharSequence? = throw error

        override fun getExtras(): Bundle? = throw error

        override fun hasExtra(name: String?): Boolean = throw error

        override fun getBundleExtra(name: String?): Bundle? = throw error
    }
}
