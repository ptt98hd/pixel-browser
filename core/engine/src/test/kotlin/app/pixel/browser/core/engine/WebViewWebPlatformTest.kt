package app.pixel.browser.core.engine

import android.content.Context
import android.net.Uri
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebView
import android.webkit.WebViewDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboWebSettings

/** Spec 007 – phần riêng của [WebViewEngineSession]: ánh xạ callback WebView ↔ EngineRequest/EngineResponse. */
@RunWith(RobolectricTestRunner::class)
class WebViewWebPlatformTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val pageUrl = "https://example.com/page"
    private val pageOrigin = "https://example.com"

    private fun session(): WebViewEngineSession =
        WebViewEngineSession(context).apply {
            loadUrl(pageUrl)
            shadowOf(webView).pushEntryToHistory(pageUrl)
            dispatchPageFinished(pageUrl)
        }

    // --- AC9: cài đặt ---

    @Test
    fun ac9_settings_supportMultipleWindows_andJsCanOpenWindows() {
        val s = WebViewEngineSession(context).webView.settings
        assertTrue(s.supportMultipleWindows())
        assertTrue(s.javaScriptCanOpenWindowsAutomatically)
    }

    @Test
    fun settings_geolocationEnabled_andMediaNeedsGesture() {
        // WebSettings không có getter công khai cho geolocation; Robolectric dùng RoboWebSettings.
        val s = WebViewEngineSession(context).webView.settings as RoboWebSettings
        assertTrue(s.geolocationEnabled)
        assertTrue(s.mediaPlaybackRequiresUserGesture)
    }

    @Test
    fun ac10_downloadListener_isInstalled() {
        assertNotNull(shadowOf(WebViewEngineSession(context).webView).downloadListener)
    }

    // --- AC2/AC3: JS dialog ---

    @Test
    fun ac2_iframeDialog_usesFrameOrigin_notPageOrigin() {
        val s = session()
        s.dispatchJsDialog(JsDialogKind.Alert, "https://ads.other.net:8443/frame", "hi", null) {}
        assertEquals(
            "https://ads.other.net:8443",
            s.requests.value
                .single()
                .origin,
        )
    }

    @Test
    fun ac2_onResult_receivesExactlyTheResponseSent_once() {
        val s = session()
        val results = mutableListOf<EngineResponse>()
        s.dispatchJsDialog(JsDialogKind.Prompt, pageUrl, "name?", "x") { results += it }
        assertTrue(results.isEmpty())
        val id =
            s.requests.value
                .single()
                .id
        assertTrue(s.respond(id, EngineResponse.PromptText("abc")))
        assertFalse(s.respond(id, EngineResponse.Dismiss()))
        assertEquals(listOf<EngineResponse>(EngineResponse.PromptText("abc")), results)
    }

    @Test
    fun ac2_promptConfirm_meansDefaultValue_isAccepted() {
        val s = session()
        val results = mutableListOf<EngineResponse>()
        s.dispatchJsDialog(JsDialogKind.Prompt, pageUrl, "name?", "def") { results += it }
        assertTrue(
            s.respond(
                s.requests.value
                    .single()
                    .id,
                EngineResponse.Confirm(),
            ),
        )
        assertEquals(listOf<EngineResponse>(EngineResponse.Confirm()), results)
    }

    @Test
    fun ac2_close_dismissesPendingDialog_viaOnResult() {
        val s = session()
        val results = mutableListOf<EngineResponse>()
        s.dispatchJsDialog(JsDialogKind.Confirm, pageUrl, "sure?", null) { results += it }
        s.close()
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), results)
    }

    @Test
    fun ac3_pageStarted_resetsSuppression() {
        val s = session()
        repeat(2) {
            s.dispatchJsDialog(JsDialogKind.Alert, pageUrl, "m$it", null) {}
        }
        val ids = s.requests.value.map { it.id }
        s.respond(ids[0], EngineResponse.Confirm())
        s.respond(ids[1], EngineResponse.Confirm(suppressFurther = true))
        val suppressed = mutableListOf<EngineResponse>()
        s.dispatchJsDialog(JsDialogKind.Alert, pageUrl, "spam", null) { suppressed += it }
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), suppressed)

        s.dispatchPageStarted("https://example.com/next")
        s.dispatchJsDialog(JsDialogKind.Alert, "https://example.com/next", "fresh", null) {}
        val fresh = s.requests.value.single() as EngineRequest.JsDialog
        assertEquals("fresh", fresh.message)
        assertFalse(fresh.canSuppress)
    }

    // --- AC4: quyền media ---

    private fun mediaRequest(vararg resources: String) =
        RecordingPermissionRequest(Uri.parse("https://cam.example.com/room"), arrayOf(*resources))

    @Test
    fun ac4_videoAndAudio_mapToCameraAndMicrophone_withRequestOrigin() {
        val s = session()
        s.chromeClient.onPermissionRequest(
            mediaRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE),
        )
        assertEquals(
            EngineRequest.PermissionPrompt(
                1,
                "https://cam.example.com",
                setOf(SitePermission.Camera, SitePermission.Microphone),
            ),
            s.requests.value.single(),
        )
    }

    @Test
    fun ac4_grantCamera_grantsVideoCaptureOnly() {
        val s = session()
        val r = mediaRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        s.chromeClient.onPermissionRequest(r)
        assertTrue(s.respond(1, EngineResponse.Grant(setOf(SitePermission.Camera))))
        assertEquals(listOf(listOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)), r.granted)
        assertEquals(0, r.denyCalls)
    }

    @Test
    fun ac4_emptyGrant_denies() {
        val s = session()
        val r = mediaRequest(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        s.chromeClient.onPermissionRequest(r)
        assertTrue(s.respond(1, EngineResponse.Grant(emptySet())))
        assertEquals(1, r.denyCalls)
        assertTrue(r.granted.isEmpty())
    }

    @Test
    fun ac4_dismiss_denies() {
        val s = session()
        val r = mediaRequest(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        s.chromeClient.onPermissionRequest(r)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
        assertEquals(1, r.denyCalls)
        assertTrue(r.granted.isEmpty())
    }

    @Test
    fun ac4_unsupportedResources_areDroppedFromRequest() {
        val s = session()
        val r =
            mediaRequest(
                PermissionRequest.RESOURCE_MIDI_SYSEX,
                PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
            )
        s.chromeClient.onPermissionRequest(r)
        assertEquals(
            setOf(SitePermission.Camera),
            (s.requests.value.single() as EngineRequest.PermissionPrompt).permissions,
        )
    }

    @Test
    fun ac4_onlyUnsupportedResources_deniedImmediately_withoutRequest() {
        val s = session()
        val r = mediaRequest(PermissionRequest.RESOURCE_MIDI_SYSEX, PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        s.chromeClient.onPermissionRequest(r)
        assertEquals(1, r.denyCalls)
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac4_canceled_removesRequest_withoutGrantOrDeny() {
        val s = session()
        val r = mediaRequest(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        s.chromeClient.onPermissionRequest(r)
        assertEquals(1, s.requests.value.size)
        s.chromeClient.onPermissionRequestCanceled(r)
        assertTrue(s.requests.value.isEmpty())
        assertEquals(0, r.denyCalls)
        assertTrue(r.granted.isEmpty())
    }

    // --- AC5: vị trí ---

    @Test
    fun ac5_geolocationPrompt_createsLocationRequest_forGivenOrigin() {
        val s = session()
        s.chromeClient.onGeolocationPermissionsShowPrompt("https://a.com", RecordingGeolocationCallback())
        assertEquals(
            EngineRequest.PermissionPrompt(1, "https://a.com", setOf(SitePermission.Location)),
            s.requests.value.single(),
        )
    }

    @Test
    fun ac5_grantLocation_invokesAllow_neverRetain() {
        val s = session()
        val cb = RecordingGeolocationCallback()
        s.chromeClient.onGeolocationPermissionsShowPrompt("https://a.com", cb)
        assertTrue(s.respond(1, EngineResponse.Grant(setOf(SitePermission.Location))))
        assertEquals(listOf(RecordingGeolocationCallback.Call("https://a.com", allow = true, retain = false)), cb.calls)
    }

    @Test
    fun ac5_dismiss_invokesDeny_neverRetain() {
        val s = session()
        val cb = RecordingGeolocationCallback()
        s.chromeClient.onGeolocationPermissionsShowPrompt("https://a.com", cb)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
        assertEquals(
            listOf(RecordingGeolocationCallback.Call("https://a.com", allow = false, retain = false)),
            cb.calls,
        )
    }

    @Test
    fun ac5_hidePrompt_removesRequest() {
        val s = session()
        val cb = RecordingGeolocationCallback()
        s.chromeClient.onGeolocationPermissionsShowPrompt("https://a.com", cb)
        assertEquals(1, s.requests.value.size)
        s.chromeClient.onGeolocationPermissionsHidePrompt()
        assertTrue(s.requests.value.isEmpty())
    }

    // --- AC6: file chooser ---

    private fun WebViewEngineSession.showChooser(
        callback: RecordingFileCallback,
        mode: Int = FileChooserParams.MODE_OPEN,
        accept: Array<String> = arrayOf("image/*"),
        capture: Boolean = false,
    ): Boolean = chromeClient.onShowFileChooser(webView, callback, TestFileChooserParams(mode, accept, capture))

    @Test
    fun ac6_onShowFileChooser_returnsTrue_andMapsMode() {
        val s = session()
        assertTrue(s.showChooser(RecordingFileCallback(), mode = FileChooserParams.MODE_OPEN_MULTIPLE))
        assertTrue((s.requests.value.single() as EngineRequest.FileChooser).allowMultiple)
    }

    @Test
    fun ac6_files_areDeliveredAsUris() {
        val s = session()
        val cb = RecordingFileCallback()
        s.showChooser(cb)
        assertTrue(s.respond(1, EngineResponse.Files(listOf("content://a/1"))))
        assertEquals(listOf(listOf(Uri.parse("content://a/1"))), cb.values)
    }

    @Test
    fun ac6_dismiss_deliversNull() {
        val s = session()
        val cb = RecordingFileCallback()
        s.showChooser(cb)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
        assertEquals(listOf<List<Uri>?>(null), cb.values)
    }

    @Test
    fun ac6_emptyFiles_deliversNull() {
        val s = session()
        val cb = RecordingFileCallback()
        s.showChooser(cb)
        assertTrue(s.respond(1, EngineResponse.Files(emptyList())))
        assertEquals(listOf<List<Uri>?>(null), cb.values)
    }

    @Test
    fun ac6_secondChooser_whilePending_receivesNullImmediately() {
        val s = session()
        val first = RecordingFileCallback()
        val second = RecordingFileCallback()
        s.showChooser(first)
        s.showChooser(second)
        assertEquals(listOf<List<Uri>?>(null), second.values)
        assertTrue(first.values.isEmpty())
        assertEquals(1, s.requests.value.size)
    }

    @Test
    fun ac6_close_deliversNullToPendingChooser() {
        val s = session()
        val cb = RecordingFileCallback()
        s.showChooser(cb)
        s.close()
        assertEquals(listOf<List<Uri>?>(null), cb.values)
    }

    // --- AC7: HTTP auth ---

    @Test
    fun ac7_credentials_reachOnResult_andAreNotStored() {
        val s = session()
        val results = mutableListOf<EngineResponse>()
        s.dispatchHttpAuth("a.com", "Admin") { results += it }
        assertTrue(s.respond(1, EngineResponse.Credentials("u", "p")))
        assertEquals(listOf<EngineResponse>(EngineResponse.Credentials("u", "p")), results)
        @Suppress("DEPRECATION")
        assertFalse(WebViewDatabase.getInstance(context).hasHttpAuthUsernamePassword())
    }

    // --- AC8: fullscreen ---

    @Test
    fun ac8_showCustomView_exposesView() {
        val s = session()
        val view = View(context)
        s.showCustomView(view, RecordingCustomViewCallback())
        assertTrue(s.state.value.isFullscreen)
        assertSame(view, s.fullscreenView)
    }

    @Test
    fun ac8_exitFullscreen_callsCustomViewHiddenOnce_thenHideClearsState() {
        val s = session()
        val cb = RecordingCustomViewCallback()
        s.showCustomView(View(context), cb)
        s.exitFullscreen()
        assertEquals(1, cb.hiddenCalls)
        s.chromeClient.onHideCustomView()
        assertFalse(s.state.value.isFullscreen)
        assertNull(s.fullscreenView)
        s.exitFullscreen()
        assertEquals(1, cb.hiddenCalls)
    }

    @Test
    fun ac8_secondShowWhileFullscreen_hidesNewCallback_andKeepsOldView() {
        val s = session()
        val oldView = View(context)
        val oldCb = RecordingCustomViewCallback()
        val newCb = RecordingCustomViewCallback()
        s.showCustomView(oldView, oldCb)
        s.showCustomView(View(context), newCb)
        assertEquals(1, newCb.hiddenCalls)
        assertEquals(0, oldCb.hiddenCalls)
        assertSame(oldView, s.fullscreenView)
    }

    @Test
    fun ac8_close_whileFullscreen_exitsFullscreenFirst() {
        val s = session()
        val cb = RecordingCustomViewCallback()
        s.showCustomView(View(context), cb)
        s.close()
        assertEquals(1, cb.hiddenCalls)
    }

    // --- AC9: popup ---

    @Test
    fun ac9_attachWindow_passesTargetWebViewToOnAttach() {
        val s = session()
        val attached = mutableListOf<WebView>()
        assertTrue(s.dispatchCreateWindow(isUserGesture = true) { attached += it })
        val target = WebViewEngineSession(context)
        assertTrue(s.respond(1, EngineResponse.AttachWindow(target)))
        assertEquals(1, attached.size)
        assertSame(target.webView, attached.single())
    }

    @Test
    fun ac9_attachWindow_withNonWebViewTarget_isRejected() {
        val s = session()
        val attached = mutableListOf<WebView>()
        s.dispatchCreateWindow(isUserGesture = true) { attached += it }
        assertFalse(s.respond(1, EngineResponse.AttachWindow(FakeEngineSession())))
        assertEquals(1, s.requests.value.size)
        assertTrue(attached.isEmpty())
    }

    @Test
    fun ac9_dismiss_doesNotAttach() {
        val s = session()
        val attached = mutableListOf<WebView>()
        s.dispatchCreateWindow(isUserGesture = true) { attached += it }
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
        assertTrue(attached.isEmpty())
    }

    @Test
    fun ac9_noGesture_returnsFalse_noNewWindow_neverAttaches() {
        val s = session()
        val attached = mutableListOf<WebView>()
        assertFalse(s.dispatchCreateWindow(isUserGesture = false) { attached += it })
        assertEquals(listOf(EngineRequest.PopupBlocked(1, pageOrigin)), s.requests.value)
        assertTrue(attached.isEmpty())
    }

    // --- AC10 ---

    @Test
    fun ac10_onCloseWindow_createsCloseWindowRequest() {
        val s = session()
        s.chromeClient.onCloseWindow(s.webView)
        assertEquals(listOf(EngineRequest.CloseWindow(1, pageOrigin)), s.requests.value)
    }

    @Test
    fun ac10_downloadStart_doesNotNavigate() {
        val s = session()
        s.emitDownloadStart("https://a.com/f.zip", null, "application/zip", -1)
        val r = s.requests.value.single() as EngineRequest.Download
        assertEquals("https://a.com/f.zip", r.url)
        assertNull(r.contentDisposition)
        assertEquals(-1L, r.contentLength)
        assertEquals(pageUrl, s.state.value.url)
    }
}
