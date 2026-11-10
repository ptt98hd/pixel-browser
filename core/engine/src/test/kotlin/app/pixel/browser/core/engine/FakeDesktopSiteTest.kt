package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 015 AC16, AC18, AC19 trên [FakeEngineSession] (JVM) + phần chỉ có ở Fake (`restoreState`, `cacheMode`). */
class FakeDesktopSiteTest : DesktopSiteContractTest() {
    override fun createSession(): EngineSession = FakeEngineSession()

    private val EngineSession.fake get() = this as FakeEngineSession

    override fun EngineSession.emitPageFinished(url: String) {
        // Đang tải đúng [url] (vd. sau goBack) thì chỉ commit; ngược lại tải rồi commit.
        if (!(fake.state.value.url == url && fake.state.value.isLoading)) fake.loadUrl(url)
        fake.simulatePageFinished(url)
    }

    override fun EngineSession.emitPageStarted(url: String) = fake.simulatePageStarted(url)

    override fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean,
    ): Boolean = fake.simulateNavigation(url, isMainFrame)

    override fun EngineSession.reloads(): Int = fake.reloadCount

    override fun EngineSession.isNoCache(): Boolean = fake.cacheMode == CacheMode.NoCache

    @Test
    fun ac16_fake_reloadCount_countsPlainReloads() {
        val s = FakeEngineSession()
        s.loadUrl("https://a.com/")
        s.simulatePageFinished("https://a.com/")
        s.reload()
        assertEquals(1, s.reloadCount)
    }

    @Test
    fun ac19_restoreState_ofPageNeedingDesktop_appliesModeAndReloadsAtMostOnce() {
        val source = FakeEngineSession()
        source.loadUrl("https://d.com/")
        source.simulatePageFinished("https://d.com/")
        val saved = source.saveState()!!

        val restored = FakeEngineSession()
        restored.setDesktopSiteResolver { it == "d.com" }
        assertTrue(restored.restoreState(saved))
        restored.simulatePageStarted("https://d.com/")
        assertTrue(restored.state.value.isDesktopSite)
        assertTrue("reloads=${restored.reloadCount}", restored.reloadCount <= 1)
        restored.simulatePageStarted("https://d.com/")
        assertTrue(restored.reloadCount <= 1)
    }
}
