package app.pixel.browser.core.engine

import android.content.Context
import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WebViewHistoryNavigationTest : HistoryNavigationContractTest() {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    override fun createSession(): EngineSession = WebViewEngineSession(context)

    override fun EngineSession.emitPageFinished(url: String) {
        this as WebViewEngineSession
        val current = webView.copyBackForwardList().currentItem?.url
        if (current != url) shadowOf(webView).pushEntryToHistory(url)
        dispatchPageFinished(url)
    }

    override fun EngineSession.observedCacheMode(): CacheMode =
        when (val mode = (this as WebViewEngineSession).webView.settings.cacheMode) {
            WebSettings.LOAD_DEFAULT -> CacheMode.Default
            WebSettings.LOAD_CACHE_ELSE_NETWORK -> CacheMode.CacheElseNetwork
            else -> throw AssertionError("Unexpected WebSettings cacheMode $mode")
        }

    @Test
    fun goBack_setsWebSettingsCacheMode_toLoadCacheElseNetwork() {
        val s = WebViewEngineSession(context)
        s.loadUrl("https://a.example/")
        s.emitPageFinished("https://a.example/")
        s.loadUrl("https://b.example/")
        s.emitPageFinished("https://b.example/")
        s.goBack()
        assertEquals(WebSettings.LOAD_CACHE_ELSE_NETWORK, s.webView.settings.cacheMode)
        s.loadUrl("https://c.example/")
        assertEquals(WebSettings.LOAD_DEFAULT, s.webView.settings.cacheMode)
    }

    @Test
    fun restoreState_withNonWebCurrentEntry_isRejected() {
        val s = WebViewEngineSession(context)
        s.loadUrl("https://a.example/")
        s.emitPageFinished("https://a.example/")
        shadowOf(s.webView).pushEntryToHistory("file:///sdcard/secret.html")
        val bytes = s.saveState()
        assertNotNull(bytes)

        val restored = WebViewEngineSession(context)
        val before = restored.state.value
        assertFalse(restored.restoreState(bytes!!))
        assertEquals(before, restored.state.value)
    }
}
