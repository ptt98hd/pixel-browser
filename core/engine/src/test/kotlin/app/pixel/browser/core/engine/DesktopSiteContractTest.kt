package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hợp đồng trang web máy tính (spec 015 B05, AC16, AC18, AC19) cho mọi [EngineSession]. Tách khỏi
 * `EngineSessionContractTest` để PR 4B (Desktop) độc lập với 4A (Find).
 */
abstract class DesktopSiteContractTest {
    protected abstract fun createSession(): EngineSession

    /** Trang [url] đã tải xong và vào lịch sử. */
    protected abstract fun EngineSession.emitPageFinished(url: String)

    /** Như `onPageStarted(url)`. */
    protected abstract fun EngineSession.emitPageStarted(url: String)

    /** Như `shouldOverrideUrlLoading`; true = engine chặn điều hướng. */
    protected abstract fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean = true,
    ): Boolean

    /** Tổng số lần session đã tải lại trang (Fake: `reloadCount`; WebView: `ShadowWebView.getReloadInvocations`). */
    protected abstract fun EngineSession.reloads(): Int

    /** Lần tải hiện tại đang dùng cache mode `NoCache` (`LOAD_NO_CACHE`). */
    protected abstract fun EngineSession.isNoCache(): Boolean

    private fun loaded(url: String = "https://a.com/"): EngineSession =
        createSession().also {
            it.loadUrl(url)
            it.emitPageFinished(url)
        }

    private val EngineSession.desktop get() = state.value.isDesktopSite

    // --- AC16 ---

    @Test
    fun ac16_newSession_isMobile() {
        assertFalse(createSession().desktop)
    }

    @Test
    fun ac16_enableOnLoadedPage_switchesMode_reloadsOnce_withNoCache() {
        val s = loaded()
        val before = s.reloads()
        s.setDesktopSite(true)
        assertTrue(s.desktop)
        assertEquals(1, s.reloads() - before)
        assertTrue(s.isNoCache())
    }

    @Test
    fun ac16_cacheMode_returnsToDefaultWhenPageFinished() {
        val s = loaded()
        s.setDesktopSite(true)
        s.emitPageFinished("https://a.com/")
        assertFalse(s.isNoCache())
    }

    @Test
    fun ac16_enableTwice_doesNotReloadAgain() {
        val s = loaded()
        s.setDesktopSite(true)
        val afterFirst = s.reloads()
        s.setDesktopSite(true)
        assertEquals(afterFirst, s.reloads())
    }

    @Test
    fun ac16_disable_switchesBack_andReloads() {
        val s = loaded()
        s.setDesktopSite(true)
        s.emitPageFinished("https://a.com/")
        val before = s.reloads()
        s.setDesktopSite(false)
        assertFalse(s.desktop)
        assertEquals(1, s.reloads() - before)
        assertTrue(s.isNoCache())
    }

    @Test
    fun ac16_disable_whenAlreadyMobile_isNoOp() {
        val s = loaded()
        val before = s.reloads()
        s.setDesktopSite(false)
        assertEquals(before, s.reloads())
    }

    @Test
    fun ac16_noPageYet_changesModeWithoutReload() {
        val s = createSession()
        s.setDesktopSite(true)
        assertTrue(s.desktop)
        assertEquals(0, s.reloads())
    }

    @Test
    fun ac16_aboutBlank_changesModeWithoutReload() {
        val s = createSession()
        s.loadUrl("about:blank")
        s.emitPageFinished("about:blank")
        s.setDesktopSite(true)
        assertTrue(s.desktop)
        assertEquals(0, s.reloads())
    }

    @Test
    fun ac16_afterClose_isNoOp() {
        val s = loaded()
        s.close()
        s.setDesktopSite(true)
        assertFalse(s.desktop)
        assertEquals(0, s.reloads())
    }

    // --- AC18 ---

    private fun EngineSession.useResolver(resolver: (String?) -> Boolean) =
        setDesktopSiteResolver(DesktopSiteResolver(resolver))

    @Test
    fun ac18_loadUrl_asksResolver_beforePageFinished_withoutExtraReload() {
        val s = createSession()
        s.useResolver { it == "a.com" }
        s.loadUrl("https://www.a.com/x")
        assertTrue(s.desktop)
        assertEquals(0, s.reloads())
        s.loadUrl("https://b.com")
        assertFalse(s.desktop)
        assertEquals(0, s.reloads())
    }

    @Test
    fun ac18_resolver_receivesSiteKey() {
        val keys = mutableListOf<String?>()
        val s = createSession()
        s.useResolver {
            keys += it
            false
        }
        s.loadUrl("https://www.A.com/x")
        assertEquals("a.com", keys.last())
    }

    @Test
    fun ac18_mainFrameNavigation_changesMode_withoutReload() {
        val s = loaded("https://b.com/")
        s.useResolver { it == "a.com" }
        s.emitNavigation("https://a.com/", isMainFrame = true)
        assertTrue(s.desktop)
        assertEquals(0, s.reloads())
        s.emitNavigation("https://b.com/", isMainFrame = true)
        assertFalse(s.desktop)
        assertEquals(0, s.reloads())
    }

    @Test
    fun ac18_subFrameNavigation_doesNotAskResolver() {
        val s = loaded("https://b.com/")
        val keys = mutableListOf<String?>()
        s.useResolver {
            keys += it
            true
        }
        s.emitNavigation("https://a.com/", isMainFrame = false)
        assertTrue(keys.isEmpty())
        assertFalse(s.desktop)
    }

    @Test
    fun ac18_nullResolver_meansMobile() {
        val s = createSession()
        s.useResolver { true }
        s.setDesktopSiteResolver(null)
        s.loadUrl("https://a.com/")
        assertFalse(s.desktop)
    }

    @Test
    fun ac18_blockedOrExternalNavigation_doesNotChangeMode() {
        var desktopAll = false
        val s = loaded("https://b.com/")
        s.useResolver { desktopAll }
        desktopAll = true
        assertTrue(s.emitNavigation("tel:123"))
        // javascript: không phải điều hướng bị chặn (NavigationPolicy: LoadInEngine) nhưng cũng không đổi chế độ.
        assertFalse(s.emitNavigation("javascript:alert(1)"))
        assertFalse(s.desktop)
        assertFalse(s.emitNavigation("https://c.com/"))
        assertTrue(s.desktop)
    }

    @Test
    fun ac18_tempOverride_beatsResolver_forSameSite_onlyThatSite() {
        val s = createSession()
        s.useResolver { false }
        s.loadUrl("https://b.com/")
        s.emitPageFinished("https://b.com/")
        s.setDesktopSite(true)
        s.loadUrl("https://b.com/other")
        assertTrue(s.desktop)
        s.loadUrl("https://c.com/")
        assertFalse(s.desktop)
        s.emitNavigation("https://www.b.com/again")
        assertTrue(s.desktop)
    }

    @Test
    fun ac18_nullSiteKey_isAppliedOnlyToCurrentPage_thenResolverTakesOver() {
        val s = createSession()
        s.useResolver { false }
        s.loadUrl("about:blank")
        s.emitPageFinished("about:blank")
        s.setDesktopSite(true)
        assertTrue(s.desktop)
        s.loadUrl("https://a.com/")
        assertFalse(s.desktop)
        s.loadUrl("about:blank")
        assertFalse(s.desktop)
    }

    @Test
    fun ac18_loadUrl_thenPageStartedOfSameUrl_doesNotReload() {
        val s = createSession()
        s.useResolver { it == "b.com" }
        s.loadUrl("https://b.com/")
        s.emitPageStarted("https://b.com/")
        assertTrue(s.desktop)
        assertEquals(0, s.reloads())
    }

    // --- AC19 ---

    @Test
    fun ac19_historyBack_toPageNeedingOtherMode_switchesAndReloadsExactlyOnce() {
        val s = createSession()
        s.useResolver { it == "b.com" }
        s.loadUrl("https://a.com/")
        s.emitPageFinished("https://a.com/")
        s.loadUrl("https://b.com/")
        s.emitPageFinished("https://b.com/")
        assertTrue(s.desktop)
        val before = s.reloads()
        s.goBack()
        s.emitPageStarted("https://a.com/")
        assertFalse(s.desktop)
        assertEquals(1, s.reloads() - before)
        // Lần onPageStarted của chính lần reload đó không được kích hoạt lại.
        s.emitPageStarted("https://a.com/")
        assertEquals(1, s.reloads() - before)
    }

    @Test
    fun ac19_historyBack_toPageWithSameMode_doesNotReload() {
        val s = createSession()
        s.useResolver { false }
        s.loadUrl("https://a.com/")
        s.emitPageFinished("https://a.com/")
        s.loadUrl("https://b.com/")
        s.emitPageFinished("https://b.com/")
        s.goBack()
        s.emitPageStarted("https://a.com/")
        assertEquals(0, s.reloads())
    }

    // --- Review: setDesktopSite(rememberInSession = false) không để lại ngoại lệ tạm ---

    @Test
    fun review_setDesktopSite_withoutRemember_leavesResolverInControl() {
        val s = createSession()
        s.useResolver { false }
        s.loadUrl("https://a.com/")
        s.emitPageFinished("https://a.com/")
        s.setDesktopSite(true, rememberInSession = false)
        assertTrue(s.desktop)
        s.loadUrl("https://a.com/next")
        assertFalse(s.desktop)
    }

    @Test
    fun review_loadUrl_ofNonWebUrl_doesNotAskResolver() {
        val keys = mutableListOf<String?>()
        val s = createSession()
        s.useResolver {
            keys += it
            true
        }
        s.loadUrl("about:blank")
        assertTrue(keys.isEmpty())
    }
}
