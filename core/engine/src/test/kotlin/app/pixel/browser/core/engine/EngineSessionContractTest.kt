package app.pixel.browser.core.engine

import android.graphics.Bitmap
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hợp đồng chung cho mọi [EngineSession]; Fake và WebView đều phải thỏa. */
abstract class EngineSessionContractTest {
    protected abstract fun createSession(): EngineSession

    /** Mô phỏng engine báo tiến độ tải. */
    protected abstract fun EngineSession.emitProgress(progress: Int)

    /** Mô phỏng engine tải xong [url] (đã vào lịch sử). */
    protected abstract fun EngineSession.emitPageFinished(url: String)

    /** Mô phỏng lỗi SSL. */
    protected abstract fun EngineSession.emitSslError()

    /** Mô phỏng engine bắt đầu commit trang [url] (`onPageStarted`). */
    protected abstract fun EngineSession.emitPageStarted(url: String)

    /** View mà engine phải expose khi còn mở (spec 005): Fake → null, WebView → `webView`. */
    protected abstract fun expectedView(session: EngineSession): View?

    /** Mô phỏng `shouldOverrideUrlLoading` (spec 006); true = engine chặn điều hướng. */
    protected abstract fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean = true,
        hasGesture: Boolean = true,
        isRedirect: Boolean = false,
    ): Boolean

    // --- Spec 007: hook mô phỏng callback của engine ---

    /**
     * JS dialog [kind] từ trang hiện tại (frame url = `state.url`). Trả request mới trong `requests`, hoặc null
     * nếu engine tự trả lời (dialog bị chặn) – khi đó [lastDelivered] là response tự động.
     */
    protected abstract fun EngineSession.emitJsDialog(
        kind: JsDialogKind,
        message: String,
        defaultValue: String? = null,
    ): EngineRequest.JsDialog?

    /** Response gần nhất mà trang/engine thực sự nhận (qua `respond` hoặc tự động); null nếu chưa có. */
    protected abstract fun EngineSession.lastDelivered(): EngineResponse?

    /** Trang (origin = origin của `state.url`) xin quyền media [permissions] (chỉ Camera/Microphone). */
    protected abstract fun EngineSession.emitMediaPermission(permissions: Set<SitePermission>)

    /** Engine tự hủy request quyền gần nhất (`onPermissionRequestCanceled`). */
    protected abstract fun EngineSession.emitLastPermissionCanceled()

    /** `onShowFileChooser`. */
    protected abstract fun EngineSession.emitFileChooser(
        acceptTypes: List<String>,
        allowMultiple: Boolean = false,
        capture: Boolean = false,
    )

    protected abstract fun EngineSession.emitHttpAuth(
        host: String,
        realm: String,
    )

    /** `onCreateWindow`; true = engine giữ popup. */
    protected abstract fun EngineSession.emitCreateWindow(isUserGesture: Boolean): Boolean

    protected abstract fun EngineSession.emitCloseWindow()

    protected abstract fun EngineSession.emitDownload(
        url: String,
        mimeType: String?,
        contentDisposition: String?,
        contentLength: Long,
    )

    /** `onShowCustomView` với một view mới. */
    protected abstract fun EngineSession.emitEnterFullscreen()

    /** Engine báo đã thoát fullscreen (`onHideCustomView`). */
    protected abstract fun EngineSession.emitHideFullscreen()

    /** Session hợp lệ để làm đích `AttachWindow` cho loại engine đang test. */
    protected abstract fun attachTarget(): EngineSession

    // --- Spec 009: favicon ---

    /** Mô phỏng `onReceivedIcon(bitmap)` (WebView: `dispatchReceivedIcon`, Fake: `simulateFavicon`). */
    protected abstract fun EngineSession.emitFavicon(bitmap: Bitmap)

    private fun icon(): Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    private val url = "https://example.com/"

    @Test
    fun loadUrl_setsUrlAndLoadingUntilFinished() {
        val s = createSession()
        s.loadUrl(url)
        assertEquals(url, s.state.value.url)
        assertTrue(s.state.value.isLoading)
        s.emitPageFinished(url)
        assertFalse(s.state.value.isLoading)
    }

    @Test
    fun progress_isClampedAndPublished() {
        val s = createSession()
        s.loadUrl(url)
        s.emitProgress(40)
        assertEquals(40, s.state.value.progress)
        s.emitProgress(250)
        assertEquals(100, s.state.value.progress)
        s.emitProgress(-5)
        assertEquals(0, s.state.value.progress)
    }

    @Test
    fun history_reflectsCanGoBackAndForward() {
        val s = createSession()
        s.loadUrl(url)
        s.emitPageFinished(url)
        assertFalse(s.state.value.canGoBack)
        s.loadUrl("https://example.org/")
        s.emitPageFinished("https://example.org/")
        assertTrue(s.state.value.canGoBack)
        assertFalse(s.state.value.canGoForward)
        s.goBack()
        s.emitPageFinished(url)
        assertEquals(url, s.state.value.url)
        assertTrue(s.state.value.canGoForward)
        assertFalse(s.state.value.canGoBack)
        s.goForward()
        s.emitPageFinished("https://example.org/")
        assertEquals("https://example.org/", s.state.value.url)
        assertFalse(s.state.value.canGoForward)
    }

    @Test
    fun reload_setsLoading_andStop_clearsIt() {
        val s = createSession()
        s.loadUrl(url)
        s.emitPageFinished(url)
        s.reload()
        assertTrue(s.state.value.isLoading)
        s.stopLoading()
        assertFalse(s.state.value.isLoading)
    }

    @Test
    fun invalidUrl_setsError_andDoesNotLoad() {
        val s = createSession()
        s.loadUrl("not a url")
        assertNotNull(s.state.value.error)
        assertFalse(s.state.value.isLoading)
    }

    @Test
    fun validLoad_clearsPreviousError() {
        val s = createSession()
        s.loadUrl("not a url")
        s.loadUrl(url)
        assertNull(s.state.value.error)
    }

    @Test
    fun sslError_marksInsecure() {
        val s = createSession()
        s.loadUrl(url)
        s.emitSslError()
        assertEquals(SecurityState.Insecure, s.state.value.securityState)
    }

    @Test
    fun afterClose_callsAreNoOps() {
        val s = createSession()
        s.loadUrl(url)
        s.close()
        val before = s.state.value
        s.loadUrl("https://example.org/")
        s.reload()
        s.goBack()
        assertEquals(before, s.state.value)
    }

    // --- Spec 005: view, scroll, securityState khi tải xong ---

    @Test
    fun view_isEngineView_whileOpen() {
        val s = createSession()
        assertSame(expectedView(s), s.view)
    }

    @Test
    fun view_isNull_afterClose() {
        val s = createSession()
        s.close()
        assertNull(s.view)
    }

    @Test
    fun scrollToTopAndBottom_doNotChangeState() {
        val s = createSession()
        s.loadUrl(url)
        s.emitPageFinished(url)
        val before = s.state.value
        s.scrollToTop()
        s.scrollToBottom()
        assertEquals(before, s.state.value)
    }

    @Test
    fun scroll_afterClose_isNoOp() {
        val s = createSession()
        s.loadUrl(url)
        s.close()
        val before = s.state.value
        s.scrollToTop()
        s.scrollToBottom()
        assertEquals(before, s.state.value)
    }

    @Test
    fun pageFinished_https_withoutSslError_isSecure() {
        val s = createSession()
        s.loadUrl(url)
        assertEquals(SecurityState.Unknown, s.state.value.securityState)
        s.emitPageFinished(url)
        assertEquals(SecurityState.Secure, s.state.value.securityState)
    }

    @Test
    fun pageFinished_http_isInsecure() {
        val s = createSession()
        s.loadUrl("http://example.com/")
        s.emitPageFinished("http://example.com/")
        assertEquals(SecurityState.Insecure, s.state.value.securityState)
    }

    @Test
    fun pageFinished_https_afterSslError_staysInsecure() {
        val s = createSession()
        s.loadUrl(url)
        s.emitSslError()
        s.emitPageFinished(url)
        assertEquals(SecurityState.Insecure, s.state.value.securityState)
    }

    @Test
    fun sslError_doesNotLeakIntoNextLoad() {
        val s = createSession()
        s.loadUrl(url)
        s.emitSslError()
        s.emitPageFinished(url)
        s.loadUrl("https://example.org/")
        s.emitPageFinished("https://example.org/")
        assertEquals(SecurityState.Secure, s.state.value.securityState)
    }

    @Test
    fun pageFinished_aboutBlank_isUnknown() {
        val s = createSession()
        s.emitPageFinished("about:blank")
        assertEquals(SecurityState.Unknown, s.state.value.securityState)
    }

    @Test
    fun pageFinished_aboutBlank_afterSecurePage_resetsToUnknown() {
        val s = createSession()
        s.loadUrl(url)
        s.emitPageFinished(url)
        s.emitPageFinished("about:blank")
        assertEquals(SecurityState.Unknown, s.state.value.securityState)
    }

    // --- Review spec 005: securityState khi trang bắt đầu, pause/resume ---

    @Test
    fun pageStarted_afterSecurePage_resetsToUnknown() {
        val s = createSession()
        s.loadUrl(url)
        s.emitPageFinished(url)
        assertEquals(SecurityState.Secure, s.state.value.securityState)
        s.emitPageStarted("https://example.org/")
        assertEquals(SecurityState.Unknown, s.state.value.securityState)
    }

    @Test
    fun pageStarted_afterSslErrorInSameLoad_staysInsecure() {
        val s = createSession()
        s.loadUrl(url)
        s.emitSslError()
        s.emitPageStarted(url)
        assertEquals(SecurityState.Insecure, s.state.value.securityState)
    }

    @Test
    fun pauseAndResume_doNotChangeState() {
        val s = createSession()
        s.loadUrl(url)
        s.emitPageFinished(url)
        val before = s.state.value
        s.onPause()
        s.onResume()
        assertEquals(before, s.state.value)
    }

    @Test
    fun pauseAndResume_afterClose_areNoOps() {
        val s = createSession()
        s.close()
        s.onPause()
        s.onResume()
        assertNull(s.view)
    }

    // --- Spec 006: EngineRequest & điều hướng ---

    private fun loadedSession(): EngineSession =
        createSession().apply {
            loadUrl(url)
            emitPageFinished(url)
        }

    @Test
    fun ac1_newSession_hasNoRequests() {
        assertTrue(createSession().requests.value.isEmpty())
    }

    @Test
    fun ac1_respond_removesRequest_andSecondRespondFails() {
        val s = loadedSession()
        s.emitNavigation("tel:1")
        s.emitNavigation("mailto:a@b.c")
        assertEquals(listOf(1L, 2L), s.requests.value.map { it.id })
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
        assertEquals(listOf(2L), s.requests.value.map { it.id })
        assertFalse(s.respond(1, EngineResponse.Dismiss()))
        assertFalse(s.respond(99, EngineResponse.Dismiss()))
    }

    @Test
    fun ac2_close_dismissesPendingRequests_andRespondFailsAfterwards() {
        val s = loadedSession()
        s.emitNavigation("tel:1")
        s.emitNavigation("tel:2")
        assertEquals(2, s.requests.value.size)
        s.close()
        assertTrue(s.requests.value.isEmpty())
        assertFalse(s.respond(1, EngineResponse.Dismiss()))
        assertFalse(s.respond(2, EngineResponse.Dismiss()))
    }

    @Test
    fun ac5_mainFrameExternalScheme_isBlocked_andEmitsExternalAppRequest() {
        val s = loadedSession()
        val before = s.state.value.url
        assertTrue(s.emitNavigation("tel:123", isMainFrame = true, hasGesture = true))
        assertEquals(before, s.state.value.url)
        assertEquals(
            listOf(
                EngineRequest.ExternalApp(
                    id = 1,
                    origin = originOf(before),
                    uri = "tel:123",
                    hasUserGesture = true,
                    isRedirect = false,
                ),
            ),
            s.requests.value,
        )
        assertEquals(
            "https://example.com",
            s.requests.value
                .single()
                .origin,
        )
    }

    @Test
    fun ac5_requestCarriesGestureAndRedirectFlags() {
        val s = loadedSession()
        assertTrue(s.emitNavigation("market://details?id=x", hasGesture = false, isRedirect = true))
        val r = s.requests.value.single() as EngineRequest.ExternalApp
        assertEquals("market://details?id=x", r.uri)
        assertFalse(r.hasUserGesture)
        assertTrue(r.isRedirect)
    }

    @Test
    fun ac5_subframeExternalScheme_isBlocked_withoutRequest() {
        val s = loadedSession()
        assertTrue(s.emitNavigation("tel:123", isMainFrame = false))
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac5_webUrl_isLoadedByEngine_withoutRequest() {
        val s = loadedSession()
        assertFalse(s.emitNavigation("https://a.com"))
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac5_fileUrl_isBlocked_withoutRequest() {
        val s = loadedSession()
        val before = s.state.value.url
        assertTrue(s.emitNavigation("file:///sdcard/a"))
        assertTrue(s.requests.value.isEmpty())
        assertEquals(before, s.state.value.url)
    }

    @Test
    fun ac5_navigationAfterClose_createsNoRequest() {
        val s = loadedSession()
        s.close()
        s.emitNavigation("tel:123")
        assertTrue(s.requests.value.isEmpty())
    }

    // --- Spec 007: nền tảng web ---

    private val origin = "https://example.com"

    @Test
    fun ac2_alert_createsJsDialogRequest_withPageOrigin_andNoDefaultValue() {
        val s = loadedSession()
        val r = s.emitJsDialog(JsDialogKind.Alert, "hi")
        val expected = EngineRequest.JsDialog(1, origin, JsDialogKind.Alert, "hi", null, canSuppress = false)
        assertEquals(expected, r)
        assertEquals(listOf(expected), s.requests.value)
    }

    @Test
    fun ac2_prompt_keepsDefaultValue_otherKindsHaveNullDefault() {
        val s = loadedSession()
        assertEquals("d", s.emitJsDialog(JsDialogKind.Prompt, "p", "d")?.defaultValue)
        val confirm = s.emitJsDialog(JsDialogKind.Confirm, "c", "ignored")
        assertEquals(JsDialogKind.Confirm, confirm?.kind)
        assertNull(confirm?.defaultValue)
        val beforeUnload = s.emitJsDialog(JsDialogKind.BeforeUnload, "b")
        assertEquals(JsDialogKind.BeforeUnload, beforeUnload?.kind)
        assertNull(beforeUnload?.defaultValue)
    }

    @Test
    fun ac2_longMessage_isNotTruncated() {
        val s = loadedSession()
        val message = "x".repeat(20_000)
        assertEquals(message, s.emitJsDialog(JsDialogKind.Alert, message)?.message)
    }

    @Test
    fun ac2_confirmResponse_isDelivered_andRemovesRequest() {
        val s = loadedSession()
        val r = s.emitJsDialog(JsDialogKind.Confirm, "ok?")!!
        assertTrue(s.respond(r.id, EngineResponse.Confirm()))
        assertEquals(EngineResponse.Confirm(), s.lastDelivered())
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac2_promptText_isDelivered() {
        val s = loadedSession()
        val r = s.emitJsDialog(JsDialogKind.Prompt, "name?", "")!!
        assertTrue(s.respond(r.id, EngineResponse.PromptText("abc")))
        assertEquals(EngineResponse.PromptText("abc"), s.lastDelivered())
    }

    @Test
    fun ac2_dismiss_isDelivered() {
        val s = loadedSession()
        val r = s.emitJsDialog(JsDialogKind.Alert, "hi")!!
        assertTrue(s.respond(r.id, EngineResponse.Dismiss()))
        assertEquals(EngineResponse.Dismiss(), s.lastDelivered())
    }

    @Test
    fun ac3_firstDialogCannotBeSuppressed_laterDialogsCan() {
        val s = loadedSession()
        val first = s.emitJsDialog(JsDialogKind.Alert, "1")!!
        assertFalse(first.canSuppress)
        s.respond(first.id, EngineResponse.Confirm())
        assertTrue(s.emitJsDialog(JsDialogKind.Alert, "2")!!.canSuppress)
        assertTrue(s.emitJsDialog(JsDialogKind.Confirm, "3")!!.canSuppress)
    }

    /** Hai dialog, dialog thứ hai được trả lời với suppressFurther = true. */
    private fun suppressedSession(response: EngineResponse = EngineResponse.Confirm(suppressFurther = true)) =
        loadedSession().apply {
            respond(emitJsDialog(JsDialogKind.Alert, "1")!!.id, EngineResponse.Confirm())
            val second = emitJsDialog(JsDialogKind.Alert, "2")!!
            assertTrue(respond(second.id, response))
        }

    @Test
    fun ac3_afterSuppress_alertConfirmPrompt_areAutoDismissed_withoutRequest() {
        val s = suppressedSession()
        for (kind in listOf(JsDialogKind.Alert, JsDialogKind.Confirm, JsDialogKind.Prompt)) {
            assertNull(kind.name, s.emitJsDialog(kind, "spam", if (kind == JsDialogKind.Prompt) "d" else null))
            assertEquals(kind.name, EngineResponse.Dismiss(), s.lastDelivered())
            assertTrue(kind.name, s.requests.value.isEmpty())
        }
    }

    @Test
    fun ac3_suppressViaDismissOrPromptText_alsoSuppresses() {
        val viaDismiss = suppressedSession(EngineResponse.Dismiss(suppressFurther = true))
        assertNull(viaDismiss.emitJsDialog(JsDialogKind.Alert, "spam"))
        val s = loadedSession()
        s.respond(s.emitJsDialog(JsDialogKind.Prompt, "1", "")!!.id, EngineResponse.PromptText("a"))
        val second = s.emitJsDialog(JsDialogKind.Prompt, "2", "")!!
        assertTrue(s.respond(second.id, EngineResponse.PromptText("b", suppressFurther = true)))
        assertNull(s.emitJsDialog(JsDialogKind.Prompt, "3", ""))
    }

    @Test
    fun ac3_afterSuppress_beforeUnload_isAutoConfirmed() {
        val s = suppressedSession()
        assertNull(s.emitJsDialog(JsDialogKind.BeforeUnload, "leave?"))
        assertEquals(EngineResponse.Confirm(), s.lastDelivered())
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac3_suppressWithoutFlag_doesNotSuppress() {
        val s = suppressedSession(EngineResponse.Confirm())
        assertNotNull(s.emitJsDialog(JsDialogKind.Alert, "3"))
    }

    /** Điều hướng main frame mới như engine thật: lệnh + `onPageStarted`. */
    @Test
    fun ac3_newMainFrameNavigation_resetsCounterAndSuppression() {
        val s = suppressedSession()
        s.loadUrl("https://example.org/")
        s.emitPageStarted("https://example.org/")
        val r = s.emitJsDialog(JsDialogKind.Alert, "new page")
        assertNotNull(r)
        assertFalse(r!!.canSuppress)
    }

    @Test
    fun ac3_reload_resetsCounterAndSuppression() {
        val s = suppressedSession()
        s.reload()
        s.emitPageStarted(url)
        val r = s.emitJsDialog(JsDialogKind.Alert, "again")
        assertNotNull(r)
        assertFalse(r!!.canSuppress)
    }

    @Test
    fun ac4_mediaRequest_createsPermissionPrompt_withPageOrigin() {
        val s = loadedSession()
        s.emitMediaPermission(setOf(SitePermission.Camera, SitePermission.Microphone))
        assertEquals(
            listOf(EngineRequest.PermissionPrompt(1, origin, setOf(SitePermission.Camera, SitePermission.Microphone))),
            s.requests.value,
        )
    }

    @Test
    fun ac4_grantSubset_andEmptyGrant_areAccepted_extraPermissionRejected() {
        val s = loadedSession()
        s.emitMediaPermission(setOf(SitePermission.Camera))
        val id =
            s.requests.value
                .single()
                .id
        assertFalse(s.respond(id, EngineResponse.Grant(setOf(SitePermission.Camera, SitePermission.Location))))
        assertEquals(1, s.requests.value.size)
        assertTrue(s.respond(id, EngineResponse.Grant(setOf(SitePermission.Camera))))
        assertTrue(s.requests.value.isEmpty())
        s.emitMediaPermission(setOf(SitePermission.Microphone))
        assertTrue(
            s.respond(
                s.requests.value
                    .single()
                    .id,
                EngineResponse.Grant(emptySet()),
            ),
        )
    }

    @Test
    fun ac4_canceledByEngine_removesRequest() {
        val s = loadedSession()
        s.emitMediaPermission(setOf(SitePermission.Camera))
        val id =
            s.requests.value
                .single()
                .id
        s.emitLastPermissionCanceled()
        assertTrue(s.requests.value.isEmpty())
        assertFalse(s.respond(id, EngineResponse.Dismiss()))
    }

    @Test
    fun ac6_fileChooser_normalizesAcceptTypes_andCarriesFlags() {
        val s = loadedSession()
        s.emitFileChooser(listOf("", " Image/* ", ".PDF"), allowMultiple = true, capture = true)
        assertEquals(
            listOf(
                EngineRequest.FileChooser(1, origin, listOf("image/*", ".pdf"), allowMultiple = true, capture = true),
            ),
            s.requests.value,
        )
    }

    @Test
    fun ac6_singleChooser_rejectsMultipleFiles_acceptsOneOrNone() {
        val s = loadedSession()
        s.emitFileChooser(listOf("image/*"))
        val r = s.requests.value.single() as EngineRequest.FileChooser
        assertFalse(r.allowMultiple)
        assertFalse(r.capture)
        assertFalse(s.respond(r.id, EngineResponse.Files(listOf("content://a/1", "content://a/2"))))
        assertTrue(s.respond(r.id, EngineResponse.Files(listOf("content://a/1"))))
    }

    @Test
    fun ac6_secondChooserWhilePending_createsNoRequest() {
        val s = loadedSession()
        s.emitFileChooser(listOf("image/*"))
        s.emitFileChooser(listOf("video/*"))
        assertEquals(1, s.requests.value.size)
        s.respond(
            s.requests.value
                .single()
                .id,
            EngineResponse.Dismiss(),
        )
        s.emitFileChooser(listOf("video/*"))
        assertEquals(1, s.requests.value.size)
    }

    @Test
    fun ac7_httpAuth_createsRequest_withHostRealmAndPageOrigin() {
        val s = loadedSession()
        s.emitHttpAuth("a.com", "Admin")
        assertEquals(listOf(EngineRequest.HttpAuth(1, origin, "a.com", "Admin")), s.requests.value)
    }

    @Test
    fun ac7_credentials_areDelivered() {
        val s = loadedSession()
        s.emitHttpAuth("a.com", "Admin")
        assertTrue(s.respond(1, EngineResponse.Credentials("u", "p")))
        assertEquals(EngineResponse.Credentials("u", "p"), s.lastDelivered())
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac7_dismiss_isDelivered() {
        val s = loadedSession()
        s.emitHttpAuth("a.com", "Admin")
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
        assertEquals(EngineResponse.Dismiss(), s.lastDelivered())
    }

    @Test
    fun ac8_enterFullscreen_setsState_andExitClearsIt() {
        val s = loadedSession()
        assertFalse(s.state.value.isFullscreen)
        assertNull(s.fullscreenView)
        s.emitEnterFullscreen()
        assertTrue(s.state.value.isFullscreen)
        s.exitFullscreen()
        s.emitHideFullscreen()
        assertFalse(s.state.value.isFullscreen)
        assertNull(s.fullscreenView)
    }

    @Test
    fun ac9_popupWithGesture_isKept_andCreatesNewWindowRequest() {
        val s = loadedSession()
        assertTrue(s.emitCreateWindow(isUserGesture = true))
        assertEquals(listOf(EngineRequest.NewWindow(1, origin)), s.requests.value)
    }

    @Test
    fun ac9_attachWindow_withValidTarget_isAccepted() {
        val s = loadedSession()
        s.emitCreateWindow(isUserGesture = true)
        assertTrue(s.respond(1, EngineResponse.AttachWindow(attachTarget())))
        assertTrue(s.requests.value.isEmpty())
    }

    @Test
    fun ac9_newWindow_canBeDismissed() {
        val s = loadedSession()
        s.emitCreateWindow(isUserGesture = true)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
    }

    @Test
    fun ac9_popupWithoutGesture_isBlocked_andReportsPopupBlocked() {
        val s = loadedSession()
        assertFalse(s.emitCreateWindow(isUserGesture = false))
        assertEquals(listOf(EngineRequest.PopupBlocked(1, origin)), s.requests.value)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
    }

    @Test
    fun ac10_closeWindow_createsCloseWindowRequest() {
        val s = loadedSession()
        s.emitCloseWindow()
        assertEquals(listOf(EngineRequest.CloseWindow(1, origin)), s.requests.value)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
    }

    @Test
    fun ac10_download_createsDownloadRequest_withoutNavigating() {
        val s = loadedSession()
        val before = s.state.value
        s.emitDownload("https://a.com/f.pdf", "application/pdf", "attachment; filename=f.pdf", 2048)
        assertEquals(
            listOf(
                EngineRequest.Download(
                    id = 1,
                    origin = origin,
                    url = "https://a.com/f.pdf",
                    mimeType = "application/pdf",
                    contentDisposition = "attachment; filename=f.pdf",
                    contentLength = 2048,
                ),
            ),
            s.requests.value,
        )
        assertEquals(before, s.state.value)
        assertTrue(s.respond(1, EngineResponse.Dismiss()))
    }

    // --- Spec 009 AC4: favicon ---

    @Test
    fun ac4_favicon_isNullOnNewSession() {
        assertNull(createSession().favicon.value)
    }

    @Test
    fun ac4_favicon_publishesReceivedIcon() {
        val s = createSession()
        val bmp = icon()
        s.emitFavicon(bmp)
        assertSame(bmp, s.favicon.value)
    }

    @Test
    fun ac4_favicon_resetsToNullOnLoadUrl() {
        val s = createSession()
        s.emitFavicon(icon())
        s.loadUrl("https://example.org/")
        assertNull(s.favicon.value)
    }

    @Test
    fun ac4_favicon_resetsToNullOnPageStarted() {
        val s = createSession()
        s.emitFavicon(icon())
        s.emitPageStarted("https://example.org/next")
        assertNull(s.favicon.value)
    }

    @Test
    fun ac4_favicon_iconAfterPageStarted_isPublishedAgain() {
        val s = createSession()
        s.emitFavicon(icon())
        s.emitPageStarted("https://example.org/next")
        val fresh = icon()
        s.emitFavicon(fresh)
        assertSame(fresh, s.favicon.value)
    }

    @Test
    fun ac4_favicon_isNullAfterClose_andLaterIconsAreIgnored() {
        val s = createSession()
        s.emitFavicon(icon())
        s.close()
        assertNull(s.favicon.value)
        s.emitFavicon(icon())
        assertNull(s.favicon.value)
    }
}
