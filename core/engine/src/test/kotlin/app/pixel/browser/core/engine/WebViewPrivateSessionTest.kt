package app.pixel.browser.core.engine

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Spec 016 AC5–AC7 – session WebView ẩn danh với `FakeProfileGateway` (Robolectric). */
@RunWith(RobolectricTestRunner::class)
class WebViewPrivateSessionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    // --- AC5 ---

    @Test
    fun ac5_privateSession_appliesProfileExactlyOnce_andIsPrivate() {
        val gateway = FakeProfileGateway()
        val session = WebViewEngineSession(context, "private_x", gateway)
        assertEquals(listOf("private_x"), gateway.appliedProfiles)
        assertTrue(session.isPrivate)
        session.close()
    }

    @Test
    fun ac5_profileIsAppliedBeforeAnyOtherWebViewSetup() {
        val gateway = FakeProfileGateway()
        var clientAtApply: WebViewClient? = null
        var chromeAtApply: WebChromeClient? = null
        var javaScriptAtApply: Boolean? = null
        var domStorageAtApply: Boolean? = null
        var applied: WebView? = null
        gateway.onApply = { webView, _ ->
            applied = webView
            clientAtApply = webView.webViewClient
            chromeAtApply = webView.webChromeClient
            javaScriptAtApply = webView.settings.javaScriptEnabled
            domStorageAtApply = webView.settings.domStorageEnabled
        }

        val session = WebViewEngineSession(context, "private_x", gateway)

        assertEquals("apply must have been called", 1, gateway.appliedProfiles.size)
        assertEquals(session.webView, applied)
        assertNull("webChromeClient must not be set yet", chromeAtApply)
        assertNotSame("webViewClient must not be the engine's yet", session.webView.webViewClient, clientAtApply)
        assertEquals("settings must not be configured yet", false, javaScriptAtApply)
        assertEquals("settings must not be configured yet", false, domStorageAtApply)
        // Sau khi khởi tạo xong engine đã cấu hình đầy đủ.
        assertNotNull(session.webView.webChromeClient)
        assertTrue(session.webView.settings.javaScriptEnabled)
        session.close()
    }

    @Test
    fun ac5_normalSession_neverAppliesProfile() {
        val gateway = FakeProfileGateway()
        val session = WebViewEngineSession(context, null, gateway)
        assertEquals(emptyList<String>(), gateway.appliedProfiles)
        assertEquals(emptyList<String>(), gateway.calls)
        assertFalse(session.isPrivate)
        session.close()
    }

    @Test
    fun ac5_defaultConstructor_isNormalSession() {
        val session = WebViewEngineSession(context)
        assertFalse(session.isPrivate)
        session.close()
    }

    // --- AC6: fail-closed ---

    @Test
    fun ac6_applyThrows_constructorThrowsPrivateUnavailable_andWebViewIsDestroyed() {
        val gateway = FakeProfileGateway()
        gateway.applyFailure = IllegalStateException("profile in use")
        var created: WebView? = null
        gateway.onApply = { webView, _ -> created = webView }

        assertThrows(PrivateUnavailableException::class.java) { WebViewEngineSession(context, "private_x", gateway) }

        val webView = checkNotNull(created) { "apply was never reached" }
        assertTrue("WebView must be destroyed, never left on the default profile", shadowOf(webView).wasDestroyCalled())
    }

    @Test
    fun ac6_applyThrowsUnsupportedOperation_isAlsoMappedToPrivateUnavailable() {
        val gateway = FakeProfileGateway()
        gateway.applyFailure = UnsupportedOperationException("MULTI_PROFILE")
        assertThrows(PrivateUnavailableException::class.java) { WebViewEngineSession(context, "private_x", gateway) }
    }

    @Test
    fun ac6_unsupportedGateway_privateSessionIsRefused_withoutApplying() {
        val gateway = FakeProfileGateway(supported = false)
        assertThrows(PrivateUnavailableException::class.java) { WebViewEngineSession(context, "private_x", gateway) }
        assertEquals(emptyList<String>(), gateway.appliedProfiles)
    }

    // --- AC7: gia cố ---

    @Test
    fun ac7_privateSession_excludesWebViewFromAutofill() {
        val session = WebViewEngineSession(context, "private_x", FakeProfileGateway())
        assertEquals(
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
            session.webView.importantForAutofill,
        )
        session.close()
    }

    @Test
    fun ac7_normalSession_keepsDefaultAutofillAndImeOptions() {
        val default = WebView(context).importantForAutofill
        val session = WebViewEngineSession(context, null, FakeProfileGateway())
        assertEquals(default, session.webView.importantForAutofill)
        assertNotEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, session.webView.importantForAutofill)
        val info = EditorInfo()
        session.webView.onCreateInputConnection(info)
        assertEquals(
            "normal session must not change imeOptions",
            0,
            info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING,
        )
        session.close()
    }

    @Test
    fun ac7_privateSession_asksKeyboardNotToLearn() {
        val session = WebViewEngineSession(context, "private_x", FakeProfileGateway())
        val info = EditorInfo()
        session.webView.onCreateInputConnection(info)
        assertNotEquals(0, info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        session.close()
    }
}
