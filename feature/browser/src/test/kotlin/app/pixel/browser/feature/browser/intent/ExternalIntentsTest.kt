package app.pixel.browser.feature.browser.intent

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 006 – AC20–AC22: [ExternalIntents.parse] (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class ExternalIntentsTest {
    private val own = "app.pixel.browser"

    private fun parse(uri: String) = ExternalIntents.parse(uri, own)

    private fun parseIntent(uri: String): Intent {
        val parsed = parse(uri)
        assertNotNull("parse($uri) must succeed", parsed)
        val intent = parsed!!.intent
        assertNotNull("parse($uri).intent", intent)
        return intent!!
    }

    // --- AC20 ---

    private val zxing =
        "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;" +
            "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fapp;end"

    @Test
    fun ac20_intentUri_parsesDataPackageBrowsableAndFallback() {
        val parsed = parse(zxing)!!
        val intent = parsed.intent!!
        assertEquals("zxing://scan/", intent.dataString)
        assertEquals("com.google.zxing.client.android", intent.`package`)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("https://example.com/app", parsed.fallbackUrl)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    @Test
    fun ac20_componentAndSelector_areStripped() {
        val intent =
            parseIntent("intent://scan/#Intent;scheme=zxing;component=com.evil/.Exported;SEL;action=x;end")
        assertNull(intent.component)
        assertNull(intent.selector)
    }

    @Test
    fun ac20_launchFlagsIncludingUriGrants_areCleared() {
        val intent = parseIntent("intent://scan/#Intent;scheme=zxing;launchFlags=0x43;end")
        assertEquals(0, intent.flags)
        assertFalse(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
    }

    @Test
    fun ac20_clipData_isNull() {
        assertNull(parseIntent(zxing).clipData)
        assertNull(parseIntent("tel:123").clipData)
    }

    // --- AC21 ---

    @Test
    fun ac21_unparseableIntentUri_isRejected() {
        // Thiếu ";end" → Intent.parseUri ném URISyntaxException.
        assertNull(parse("intent://scan/#Intent;scheme=zxing;package=com.x"))
    }

    @Test
    fun ac21_intentTargetingOwnPackage_isRejected() {
        assertNull(parse("intent://open#Intent;scheme=myapp;package=$own;end"))
    }

    @Test
    fun ac21_dangerousDataSchemes_areRejected() {
        val uris =
            listOf(
                "intent://x/y#Intent;scheme=file;end",
                "intent://x/y#Intent;scheme=content;package=com.x;end",
                "intent:alert(1)#Intent;scheme=javascript;end",
                "file:///sdcard/a",
                "content://x/y",
                "javascript:alert(1)",
            )
        for (uri in uris) assertNull(uri, parse(uri))
    }

    @Test
    fun ac21_actionsOtherThanView_areRejected() {
        val uris =
            listOf(
                "intent://123#Intent;scheme=tel;action=android.intent.action.CALL;end",
                "intent://x#Intent;scheme=myapp;action=android.intent.action.SEND;end",
                "intent://x#Intent;scheme=myapp;action=android.intent.action.DELETE;end",
            )
        for (uri in uris) assertNull(uri, parse(uri))
    }

    @Test
    fun ac21_missingAction_defaultsToView() {
        assertEquals(Intent.ACTION_VIEW, parseIntent("intent://scan/#Intent;scheme=zxing;end").action)
        assertEquals(
            Intent.ACTION_VIEW,
            parseIntent("intent://scan/#Intent;scheme=zxing;action=android.intent.action.VIEW;end").action,
        )
    }

    @Test
    fun ac21_nonWebFallback_isDropped_butIntentStaysValid() {
        val parsed =
            parse("intent://scan/#Intent;scheme=zxing;package=com.x;S.browser_fallback_url=javascript%3Aalert(1);end")
        assertNotNull(parsed)
        assertNotNull(parsed!!.intent)
        assertNull(parsed.fallbackUrl)
    }

    // --- AC22 ---

    @Test
    fun ac22_plainSchemes_becomeBrowsableView_neverCall() {
        for (uri in listOf("tel:123", "mailto:a@b.c", "market://details?id=x")) {
            val intent = parseIntent(uri)
            assertEquals(uri, Intent.ACTION_VIEW, intent.action)
            assertEquals(uri, uri, intent.dataString)
            assertTrue(uri, intent.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertNull(uri, intent.component)
            assertEquals(uri, 0, intent.flags)
        }
    }

    @Test
    fun ac22_intentUriPointingToWeb_withoutPackage_yieldsWebUrlOnly() {
        val parsed = parse("intent://example.com/a#Intent;scheme=https;end")
        assertNotNull(parsed)
        assertNull(parsed!!.intent)
        assertEquals("https://example.com/a", parsed.webUrl)
    }
}
