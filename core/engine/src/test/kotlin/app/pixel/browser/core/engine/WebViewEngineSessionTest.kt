package app.pixel.browser.core.engine

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.view.View
import android.webkit.WebSettings
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WebViewEngineSessionTest : EngineSessionContractTest() {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    override fun createSession(): EngineSession = WebViewEngineSession(context)

    override fun expectedView(session: EngineSession): View? = (session as WebViewEngineSession).webView

    override fun EngineSession.emitProgress(progress: Int) = (this as WebViewEngineSession).dispatchProgress(progress)

    override fun EngineSession.emitPageFinished(url: String) {
        this as WebViewEngineSession
        // Robolectric không tự commit lịch sử khi loadUrl; mô phỏng như engine thật.
        val current = webView.copyBackForwardList().currentItem?.url
        if (current != url) shadowOf(webView).pushEntryToHistory(url)
        dispatchPageFinished(url)
    }

    override fun EngineSession.emitSslError() = (this as WebViewEngineSession).dispatchSslError()

    override fun EngineSession.emitPageStarted(url: String) = (this as WebViewEngineSession).dispatchPageStarted(url)

    @Test
    fun defaultSettings_areSecure() {
        val s = WebViewEngineSession(context).webView.settings
        assertTrue(s.javaScriptEnabled)
        assertTrue(s.domStorageEnabled)
        assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, s.mixedContentMode)
        // safeBrowsingEnabled không được Robolectric mô phỏng; cần instrumented test (xem spec 001).
        assertFalse(s.allowFileAccess)
        assertFalse(s.allowContentAccess)
    }

    @Test
    fun close_destroysWebView() {
        val s = WebViewEngineSession(context)
        s.close()
        assertTrue(s.isClosed)
    }

    @Test
    fun pause_resume_forwardToWebView() {
        val s = WebViewEngineSession(context)
        s.onPause()
        assertTrue(shadowOf(s.webView).wasOnPauseCalled())
        s.onResume()
        assertTrue(shadowOf(s.webView).wasOnResumeCalled())
    }

    @Test
    fun close_detachesFromParent_andResetsMutableContextToApplication() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapper = MutableContextWrapper(context)
        val s = WebViewEngineSession(wrapper)
        wrapper.baseContext = activity
        val parent = FrameLayout(activity).apply { addView(s.webView) }
        s.close()
        assertNull(s.webView.parent)
        assertEquals(0, parent.childCount)
        assertSame(context.applicationContext, wrapper.baseContext)
    }
}
