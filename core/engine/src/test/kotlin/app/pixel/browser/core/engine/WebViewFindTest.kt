package app.pixel.browser.core.engine

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Spec 015 AC1–AC3 trên [WebViewEngineSession] (Robolectric). Ánh xạ sang `findAllAsync`/`findNext`/`clearMatches`
 * và `FindListener` thật không kiểm được ở đây (xem `FindInPageInstrumentedTest`).
 */
@RunWith(RobolectricTestRunner::class)
class WebViewFindTest : FindContractTest() {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    override fun createSession(): EngineSession = WebViewEngineSession(context)

    private val EngineSession.web get() = this as WebViewEngineSession

    override fun EngineSession.emitFindResult(
        active: Int,
        total: Int,
        done: Boolean,
    ) = web.dispatchFindResult(active, total, done)

    override fun EngineSession.emitPageStarted(url: String) = web.dispatchPageStarted(url)

    override fun EngineSession.emitPageFinished(url: String) {
        val current =
            web.webView
                .copyBackForwardList()
                .currentItem
                ?.url
        if (current != url) shadowOf(web.webView).pushEntryToHistory(url)
        web.dispatchPageFinished(url)
    }

    @Test
    fun ac3_find_onRealWebView_doesNotThrow() {
        val s = WebViewEngineSession(context)
        s.find("abc")
        s.findNext(true)
        s.clearFind()
        assertEquals(FindState(), s.findState.value)
    }
}
