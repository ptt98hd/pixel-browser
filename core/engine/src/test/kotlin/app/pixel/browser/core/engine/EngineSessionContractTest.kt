package app.pixel.browser.core.engine

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
}
