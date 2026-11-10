package app.pixel.browser.core.engine

import android.content.Context
import android.webkit.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Spec 015 AC16–AC19 trên [WebViewEngineSession] (Robolectric): hợp đồng chung + cài đặt WebSettings (AC17). */
@RunWith(RobolectricTestRunner::class)
class WebViewDesktopSiteTest : DesktopSiteContractTest() {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    override fun createSession(): EngineSession = WebViewEngineSession(context)

    private val EngineSession.web get() = this as WebViewEngineSession

    override fun EngineSession.emitPageFinished(url: String) {
        val current =
            web.webView
                .copyBackForwardList()
                .currentItem
                ?.url
        if (current != url) shadowOf(web.webView).pushEntryToHistory(url)
        web.dispatchPageFinished(url)
    }

    override fun EngineSession.emitPageStarted(url: String) = web.dispatchPageStarted(url)

    override fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean,
    ): Boolean = web.dispatchNavigation(url, isMainFrame, hasGesture = true, isRedirect = false)

    override fun EngineSession.reloads(): Int = shadowOf(web.webView).reloadInvocations

    override fun EngineSession.isNoCache(): Boolean = web.webView.settings.cacheMode == WebSettings.LOAD_NO_CACHE

    // --- AC17 ---

    private class Initial(
        val ua: String,
        val wideViewPort: Boolean,
        val overview: Boolean,
        val supportZoom: Boolean,
        val builtInZoom: Boolean,
        val displayZoom: Boolean,
    )

    private fun WebSettings.snapshot() =
        Initial(
            userAgentString,
            useWideViewPort,
            loadWithOverviewMode,
            supportZoom(),
            builtInZoomControls,
            displayZoomControls,
        )

    @Test
    fun ac17_enable_setsDerivedUserAgent_andWideViewportSettings() {
        val s = WebViewEngineSession(context)
        val initial = s.webView.settings.snapshot()
        s.setDesktopSite(true)
        val st = s.webView.settings
        assertEquals(DesktopUserAgent.derive(initial.ua), st.userAgentString)
        assertTrue(st.useWideViewPort)
        assertTrue(st.loadWithOverviewMode)
        assertTrue(st.supportZoom())
        assertTrue(st.builtInZoomControls)
        assertFalse(st.displayZoomControls)
    }

    @Test
    fun ac17_disable_restoresExactlyTheValuesReadAtCreation() {
        val s = WebViewEngineSession(context)
        val initial = s.webView.settings.snapshot()
        s.setDesktopSite(true)
        s.setDesktopSite(false)
        val st = s.webView.settings.snapshot()
        assertEquals(initial.ua, st.ua)
        assertEquals(initial.wideViewPort, st.wideViewPort)
        assertEquals(initial.overview, st.overview)
        assertEquals(initial.builtInZoom, st.builtInZoom)
        assertEquals(initial.displayZoom, st.displayZoom)
    }

    @Test
    fun ac17_resolverDrivenNavigation_appliesSettingsBeforeRequest() {
        val s = WebViewEngineSession(context)
        val initialUa = s.webView.settings.userAgentString
        s.setDesktopSiteResolver { it == "a.com" }
        s.loadUrl("https://a.com/")
        assertTrue(s.state.value.isDesktopSite)
        assertEquals(DesktopUserAgent.derive(initialUa), s.webView.settings.userAgentString)
        assertTrue(s.webView.settings.useWideViewPort)
        s.loadUrl("https://b.com/")
        assertEquals(initialUa, s.webView.settings.userAgentString)
    }

    @Test
    fun ac16_webView_setDesktopSite_changesUserAgentSetting() {
        val s = WebViewEngineSession(context)
        s.loadUrl("https://a.com/")
        s.emitPageFinished("https://a.com/")
        val before = s.webView.settings.userAgentString
        s.setDesktopSite(true)
        assertEquals(DesktopUserAgent.derive(before), s.webView.settings.userAgentString)
    }
}
