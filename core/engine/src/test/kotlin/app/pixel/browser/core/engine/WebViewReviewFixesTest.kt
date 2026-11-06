package app.pixel.browser.core.engine

import android.content.Context
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient.FileChooserParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Review spec 007: engine chỉ chuyển URI `content://` cho trang; exitFullscreen bỏ view ngay. */
@RunWith(RobolectricTestRunner::class)
class WebViewReviewFixesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun session(): WebViewEngineSession =
        WebViewEngineSession(context).apply {
            loadUrl("https://a.com/")
            shadowOf(webView).pushEntryToHistory("https://a.com/")
            dispatchPageFinished("https://a.com/")
        }

    private fun chooser(
        s: WebViewEngineSession,
        multiple: Boolean,
    ): RecordingFileCallback {
        val cb = RecordingFileCallback()
        val mode = if (multiple) FileChooserParams.MODE_OPEN_MULTIPLE else FileChooserParams.MODE_OPEN
        s.chromeClient.onShowFileChooser(s.webView, cb, TestFileChooserParams(mode, arrayOf("*/*"), false))
        return cb
    }

    @Test
    fun nonContentUris_areDropped() {
        val s = session()
        val cb = chooser(s, multiple = true)
        s.respond(1, EngineResponse.Files(listOf("file:///data/x", "content://media/1", "/data/y", "http://a.com/z")))
        assertEquals(listOf(listOf(Uri.parse("content://media/1"))), cb.values)
    }

    @Test
    fun onlyNonContentUris_deliverNull() {
        val s = session()
        val cb = chooser(s, multiple = false)
        s.respond(1, EngineResponse.Files(listOf("file:///data/x")))
        assertEquals(listOf<List<Uri>?>(null), cb.values)
    }

    @Test
    fun exitFullscreen_clearsViewImmediately() {
        val s = session()
        s.showCustomView(View(context), RecordingCustomViewCallback())
        s.exitFullscreen()
        assertNull(s.fullscreenView)
    }
}
