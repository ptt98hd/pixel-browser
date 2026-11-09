package app.pixel.browser.core.engine

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
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

    override fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean = (this as WebViewEngineSession).dispatchNavigation(url, isMainFrame, hasGesture, isRedirect)

    // --- Spec 007 hooks ---

    /** Mọi response mà callback của WebView nhận (JS dialog, HTTP auth), theo thứ tự. */
    private val delivered = mutableListOf<EngineResponse>()
    private val permissionRequests = mutableListOf<RecordingPermissionRequest>()

    private val EngineSession.web get() = this as WebViewEngineSession

    override fun EngineSession.emitJsDialog(
        kind: JsDialogKind,
        message: String,
        defaultValue: String?,
    ): EngineRequest.JsDialog? {
        val before = requests.value.map { it.id }.toSet()
        web.dispatchJsDialog(kind, state.value.url, message, defaultValue) { delivered += it }
        return requests.value.filterIsInstance<EngineRequest.JsDialog>().singleOrNull { it.id !in before }
    }

    override fun EngineSession.lastDelivered(): EngineResponse? = delivered.lastOrNull()

    override fun EngineSession.emitMediaPermission(permissions: Set<SitePermission>) {
        val resources =
            permissions.map {
                when (it) {
                    SitePermission.Camera -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    SitePermission.Microphone -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    SitePermission.Location -> error("Location đi qua onGeolocationPermissionsShowPrompt")
                }
            }
        val request = RecordingPermissionRequest(Uri.parse(state.value.url), resources.toTypedArray())
        permissionRequests += request
        web.chromeClient.onPermissionRequest(request)
    }

    override fun EngineSession.emitLastPermissionCanceled() =
        web.chromeClient.onPermissionRequestCanceled(permissionRequests.last())

    override fun EngineSession.emitFileChooser(
        acceptTypes: List<String>,
        allowMultiple: Boolean,
        capture: Boolean,
    ) {
        val mode =
            if (allowMultiple) {
                WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
            } else {
                WebChromeClient.FileChooserParams.MODE_OPEN
            }
        web.chromeClient.onShowFileChooser(
            web.webView,
            RecordingFileCallback(),
            TestFileChooserParams(mode, acceptTypes.toTypedArray(), capture),
        )
    }

    override fun EngineSession.emitHttpAuth(
        host: String,
        realm: String,
    ) = web.dispatchHttpAuth(host, realm) { delivered += it }

    override fun EngineSession.emitCreateWindow(isUserGesture: Boolean): Boolean =
        web.dispatchCreateWindow(isUserGesture) {}

    override fun EngineSession.emitCloseWindow() = web.chromeClient.onCloseWindow(web.webView)

    override fun EngineSession.emitDownload(
        url: String,
        mimeType: String?,
        contentDisposition: String?,
        contentLength: Long,
    ) = web.emitDownloadStart(url, contentDisposition, mimeType, contentLength)

    override fun EngineSession.emitEnterFullscreen() = web.showCustomView(View(context), RecordingCustomViewCallback())

    override fun EngineSession.emitHideFullscreen() = web.chromeClient.onHideCustomView()

    override fun attachTarget(): EngineSession = WebViewEngineSession(context)

    override fun EngineSession.emitFavicon(bitmap: Bitmap) = web.dispatchReceivedIcon(bitmap)

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
