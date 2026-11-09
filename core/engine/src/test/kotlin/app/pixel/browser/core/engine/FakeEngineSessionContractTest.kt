package app.pixel.browser.core.engine

import android.graphics.Bitmap
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FakeEngineSessionContractTest : EngineSessionContractTest() {
    override fun createSession(): EngineSession = FakeEngineSession()

    override fun expectedView(session: EngineSession): View? = null

    override fun EngineSession.emitProgress(progress: Int) = (this as FakeEngineSession).simulateProgress(progress)

    override fun EngineSession.emitPageFinished(url: String) = (this as FakeEngineSession).simulatePageFinished(url)

    override fun EngineSession.emitSslError() = (this as FakeEngineSession).simulateSslError()

    override fun EngineSession.emitPageStarted(url: String) = (this as FakeEngineSession).simulatePageStarted(url)

    override fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean = (this as FakeEngineSession).simulateNavigation(url, isMainFrame, hasGesture, isRedirect)

    // --- Spec 007 hooks ---

    private val EngineSession.fake get() = this as FakeEngineSession

    override fun EngineSession.emitJsDialog(
        kind: JsDialogKind,
        message: String,
        defaultValue: String?,
    ): EngineRequest.JsDialog? = fake.simulateJsDialog(kind, message, defaultValue)

    override fun EngineSession.lastDelivered(): EngineResponse? = fake.responses.maxByOrNull { it.key }?.value

    override fun EngineSession.emitMediaPermission(permissions: Set<SitePermission>) =
        fake.simulatePermissionRequest(permissions)

    override fun EngineSession.emitLastPermissionCanceled() =
        fake.simulatePermissionCanceled(
            requests.value
                .filterIsInstance<EngineRequest.PermissionPrompt>()
                .last()
                .id,
        )

    override fun EngineSession.emitFileChooser(
        acceptTypes: List<String>,
        allowMultiple: Boolean,
        capture: Boolean,
    ) = fake.simulateFileChooser(acceptTypes, allowMultiple, capture)

    override fun EngineSession.emitHttpAuth(
        host: String,
        realm: String,
    ) = fake.simulateHttpAuth(host, realm)

    override fun EngineSession.emitCreateWindow(isUserGesture: Boolean): Boolean =
        fake.simulateCreateWindow(isUserGesture)

    override fun EngineSession.emitCloseWindow() = fake.simulateCloseWindow()

    override fun EngineSession.emitDownload(
        url: String,
        mimeType: String?,
        contentDisposition: String?,
        contentLength: Long,
    ) = fake.simulateDownload(url, mimeType, contentDisposition, contentLength)

    override fun EngineSession.emitEnterFullscreen() = fake.simulateEnterFullscreen()

    override fun EngineSession.emitHideFullscreen() = fake.simulateExitFullscreen()

    override fun attachTarget(): EngineSession = FakeEngineSession()

    override fun EngineSession.emitFavicon(bitmap: Bitmap) = fake.simulateFavicon(bitmap)

    // --- Spec 005 ---

    @Test
    fun scrollCommands_startEmpty() {
        assertTrue(FakeEngineSession().scrollCommands.isEmpty())
    }

    @Test
    fun scrollCommands_recordTopAndBottomInOrder() {
        val s = FakeEngineSession()
        s.scrollToTop()
        s.scrollToBottom()
        s.scrollToTop()
        assertEquals(listOf(ScrollCommand.Top, ScrollCommand.Bottom, ScrollCommand.Top), s.scrollCommands)
    }

    @Test
    fun scrollCommands_notRecordedAfterClose() {
        val s = FakeEngineSession()
        s.scrollToTop()
        s.close()
        s.scrollToBottom()
        assertEquals(listOf(ScrollCommand.Top), s.scrollCommands)
    }

    @Test
    fun pause_resume_areRecorded_andIgnoredAfterClose() {
        val s = FakeEngineSession()
        assertFalse(s.isPaused)
        s.onPause()
        assertTrue(s.isPaused)
        s.onResume()
        assertFalse(s.isPaused)
        s.close()
        s.onPause()
        assertFalse(s.isPaused)
    }

    // --- Spec 006 ---

    @Test
    fun ac5_simulateNavigation_defaults_areMainFrameWithGestureNoRedirect() {
        val s = FakeEngineSession()
        assertTrue(s.simulateNavigation("tel:123"))
        val r = s.requests.value.single() as EngineRequest.ExternalApp
        assertTrue(r.hasUserGesture)
        assertFalse(r.isRedirect)
        assertEquals("", r.origin)
    }

    @Test
    fun responses_recordAcceptedResponses_only() {
        val s = FakeEngineSession()
        s.simulateNavigation("tel:1")
        s.simulateNavigation("tel:2")
        s.respond(1, EngineResponse.Dismiss(suppressFurther = true))
        s.respond(99, EngineResponse.Dismiss())
        assertEquals(mapOf(1L to EngineResponse.Dismiss(suppressFurther = true)), s.responses)
    }

    @Test
    fun ac2_close_recordsDismissForEveryPendingRequest() {
        val s = FakeEngineSession()
        s.simulateNavigation("tel:1")
        s.simulateNavigation("tel:2")
        s.close()
        assertEquals(mapOf(1L to EngineResponse.Dismiss(), 2L to EngineResponse.Dismiss()), s.responses)
    }

    // --- Spec 007 ---

    private fun loaded(url: String = "https://a.com/") =
        FakeEngineSession().apply {
            loadUrl(url)
            simulatePageFinished(url)
        }

    /** Trang hiện tại đã bật chặn dialog (dialog thứ hai trả lời với suppressFurther = true). */
    private fun FakeEngineSession.suppressDialogs() {
        respond(simulateJsDialog(JsDialogKind.Alert, "1")!!.id, EngineResponse.Confirm())
        respond(simulateJsDialog(JsDialogKind.Alert, "2")!!.id, EngineResponse.Confirm(suppressFurther = true))
        assertNull(simulateJsDialog(JsDialogKind.Alert, "check"))
    }

    @Test
    fun ac3_suppressedDialog_returnsNull_andRecordsDismissResponse() {
        val s = loaded()
        s.suppressDialogs()
        val before = s.responses.keys
        assertNull(s.simulateJsDialog(JsDialogKind.Confirm, "spam"))
        val added = s.responses - before
        assertEquals(1, added.size)
        assertEquals(EngineResponse.Dismiss(), added.values.single())
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac3_loadUrl_aloneResetsSuppression() {
        val s = loaded()
        s.suppressDialogs()
        s.loadUrl("https://b.com/")
        assertFalse(s.simulateJsDialog(JsDialogKind.Alert, "x")!!.canSuppress)
    }

    @Test
    fun ac3_reload_aloneResetsSuppression() {
        val s = loaded()
        s.suppressDialogs()
        s.reload()
        assertFalse(s.simulateJsDialog(JsDialogKind.Alert, "x")!!.canSuppress)
    }

    @Test
    fun ac3_goBack_andGoForward_aloneResetSuppression() {
        val s = loaded()
        s.loadUrl("https://b.com/")
        s.simulatePageFinished("https://b.com/")
        s.suppressDialogs()
        s.goBack()
        assertFalse(s.simulateJsDialog(JsDialogKind.Alert, "back")!!.canSuppress)
        s.simulatePageFinished("https://a.com/")
        s.suppressDialogs()
        s.goForward()
        assertFalse(s.simulateJsDialog(JsDialogKind.Alert, "forward")!!.canSuppress)
    }

    @Test
    fun ac4_simulatePermissionRequest_usesExplicitOrigin() {
        val s = loaded()
        s.simulatePermissionRequest(setOf(SitePermission.Location), origin = "https://maps.example")
        assertEquals(
            EngineRequest.PermissionPrompt(1, "https://maps.example", setOf(SitePermission.Location)),
            s.requests.value.single(),
        )
    }

    @Test
    fun ac4_permissionCanceled_recordsNoResponse() {
        val s = loaded()
        s.simulatePermissionRequest(setOf(SitePermission.Camera))
        s.simulatePermissionCanceled(1)
        assertTrue(s.responses.isEmpty())
    }

    @Test
    fun ac8_exitFullscreen_countsCalls_andLeavesFullscreen() {
        val s = loaded()
        assertEquals(0, s.exitFullscreenCalls)
        s.simulateEnterFullscreen()
        assertTrue(s.state.value.isFullscreen)
        assertNull(s.fullscreenView)
        s.exitFullscreen()
        assertEquals(1, s.exitFullscreenCalls)
        assertFalse(s.state.value.isFullscreen)
    }

    @Test
    fun ac9_attachWindow_isRecorded() {
        val s = loaded()
        assertTrue(s.simulateCreateWindow(true))
        val target = FakeEngineSession()
        assertTrue(s.respond(1, EngineResponse.AttachWindow(target)))
        assertSame(target, (s.responses[1L] as EngineResponse.AttachWindow).target)
    }
}
