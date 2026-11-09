package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.isValidWebUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bọc [FakeEngineSession] và đếm lời gọi điều hướng (spec 010). */
class CountingSession(
    val fake: FakeEngineSession = FakeEngineSession(),
) : EngineSession by fake {
    val loadUrlCalls = mutableListOf<String>()
    var goBackCalls = 0
        private set
    var stopLoadingCalls = 0
        private set

    override fun loadUrl(url: String) {
        loadUrlCalls += url
        fake.loadUrl(url)
    }

    override fun goBack() {
        goBackCalls++
        fake.goBack()
    }

    override fun stopLoading() {
        stopLoadingCalls++
        fake.stopLoading()
    }
}

class CountingSessionFactory : EngineSessionFactory {
    val created = mutableListOf<CountingSession>()

    override fun create(): EngineSession = CountingSession().also { created += it }
}

/** Spec 010 – AC1–AC5: home là trạng thái của tab trong [TabManager] (JVM thuần). */
class TabManagerHomeTest {
    private val factory = CountingSessionFactory()
    private val store = InMemoryTabSnapshotStore()

    private val a = "https://a.com/"
    private val b = "https://b.com/"

    private fun manager() = TabManager(factory, store)

    private fun TabManager.tab(id: String): Tab = tabs.value.single { it.id == id }

    private fun TabManager.spy(id: String): CountingSession = session(id) as CountingSession

    private fun TabManager.selectedId(): String = selectedTabId.value!!

    /** `loadUrl` qua TabManager rồi commit trang. */
    private fun TabManager.go(
        id: String,
        url: String,
    ) {
        loadUrl(id, url)
        spy(id).fake.simulatePageFinished(url)
    }

    // --- AC1 ---

    @Test
    fun ac1_homeUrl_isPixelHome_andNotAWebUrl() {
        assertEquals("pixel://home", HOME_URL)
        assertFalse(isValidWebUrl(HOME_URL))
    }

    @Test
    fun ac1_emptyStore_startsWithHomeTab_withoutLoadingAnything() {
        val m = manager()
        val tab = m.tabs.value.single()
        assertTrue(tab.isHome)
        assertFalse(tab.backToHome)
        val s = m.spy(tab.id)
        assertTrue(s.loadUrlCalls.isEmpty())
        assertTrue(s.fake.loadedUrls.isEmpty())
        assertEquals("", s.state.value.url)
    }

    @Test
    fun ac1_openTabHomeUrl_createsHomeTab_withoutLoad() {
        val m = manager()
        val t = m.openTab(HOME_URL)
        assertTrue(t.isHome)
        assertTrue(m.tab(t.id).isHome)
        assertTrue(m.spy(t.id).loadUrlCalls.isEmpty())
        assertEquals(
            "",
            m
                .spy(t.id)
                .state.value.url,
        )
    }

    @Test
    fun ac1_tabIsHome_onlyForHomeUrl() {
        assertTrue(Tab("x", HOME_URL).isHome)
        assertFalse(Tab("x", "https://a.com/").isHome)
        assertFalse(Tab("x", "about:blank").isHome)
    }

    // --- AC2 ---

    @Test
    fun ac2_loadUrlFromHome_leavesHome_setsBackToHome_andLoadsOnce() {
        val m = manager()
        val h = m.selectedId()
        m.loadUrl(h, "https://a.com")
        val tab = m.tab(h)
        assertFalse(tab.isHome)
        assertTrue(tab.backToHome)
        assertEquals(listOf("https://a.com"), m.spy(h).loadUrlCalls)
    }

    @Test
    fun ac2_loadUrlInRegularTab_keepsBackToHomeFalse() {
        val m = manager()
        val t = m.openTab(a)
        m.spy(t.id).fake.simulatePageFinished(a)
        m.loadUrl(t.id, b)
        assertFalse(m.tab(t.id).backToHome)
        assertFalse(m.tab(t.id).isHome)
        assertEquals(listOf(a, b), m.spy(t.id).loadUrlCalls)
    }

    @Test
    fun ac2_loadUrlUnknownId_isNoOp() {
        val m = manager()
        val before = m.tabs.value
        m.loadUrl("does-not-exist", a)
        assertEquals(before, m.tabs.value)
        assertTrue(factory.created.all { it.loadUrlCalls.isEmpty() })
    }

    // --- AC3 ---

    @Test
    fun ac3_showHome_overlaysPage_stopsLoading_keepsSessionAndHistory() {
        val m = manager()
        val t = m.openTab(a)
        val s = m.spy(t.id)
        s.fake.simulatePageFinished(a)
        m.go(t.id, b)
        s.fake.simulateTitle("B")
        s.fake.reload() // đang tải
        assertTrue(s.state.value.isLoading)

        m.showHome(t.id)

        val tab = m.tab(t.id)
        assertEquals(HOME_URL, tab.url)
        assertEquals("", tab.title)
        assertTrue(tab.isHome)
        assertFalse(s.fake.isClosed)
        assertFalse(s.state.value.isLoading)
        assertTrue(s.stopLoadingCalls >= 1)
        assertTrue(s.state.value.canGoBack)
    }

    @Test
    fun ac3_backFromHomeOverPage_returnsToPage_withoutGoBack() {
        val m = manager()
        val t = m.openTab(a)
        val s = m.spy(t.id)
        s.fake.simulatePageFinished(a)
        m.go(t.id, b)
        m.showHome(t.id)

        assertTrue(m.canNavigateBack(t.id))
        assertTrue(m.navigateBack(t.id))

        val tab = m.tab(t.id)
        assertFalse(tab.isHome)
        assertEquals(b, tab.url)
        assertEquals(0, s.goBackCalls)
        assertEquals(listOf(a, b), s.loadUrlCalls)
    }

    @Test
    fun ac3_atHome_engineStateChanges_doNotLeaveHome() {
        val m = manager()
        val t = m.openTab(a)
        m.spy(t.id).fake.simulatePageFinished(a)
        m.showHome(t.id)
        m.spy(t.id).fake.simulateTitle("Background title")
        m.selectTab(t.id) // publish lại
        assertTrue(m.tab(t.id).isHome)
        assertEquals("", m.tab(t.id).title)
    }

    // --- AC4 ---

    @Test
    fun ac4_backChainFromHome_goBack_thenHome_thenNothing() {
        val m = manager()
        val h = m.selectedId()
        val s = m.spy(h)
        m.go(h, a)
        m.go(h, b)

        assertTrue(m.navigateBack(h))
        assertEquals(1, s.goBackCalls)
        s.fake.simulatePageFinished(a)
        assertFalse(m.tab(h).isHome)
        assertEquals(a, m.tab(h).url)

        assertTrue(m.navigateBack(h))
        assertTrue(m.tab(h).isHome)
        assertEquals(1, s.goBackCalls)

        assertFalse(m.canNavigateBack(h))
        assertFalse(m.navigateBack(h))
        assertTrue(m.tab(h).isHome)
    }

    @Test
    fun ac4_regularTabOnFirstPage_cannotNavigateBack() {
        val m = manager()
        val t = m.openTab(a)
        m.spy(t.id).fake.simulatePageFinished(a)
        assertFalse(m.canNavigateBack(t.id))
        assertFalse(m.navigateBack(t.id))
        assertFalse(m.tab(t.id).isHome)
    }

    @Test
    fun ac4_canNavigateBack_unknownId_isFalse() {
        val m = manager()
        assertFalse(m.canNavigateBack("does-not-exist"))
        assertFalse(m.navigateBack("does-not-exist"))
    }

    // --- AC5 ---

    @Test
    fun ac5_homeOverPageWithBackToHome_noBackLoop() {
        val m = manager()
        val t = m.selectedId()
        m.go(t, a)
        assertTrue(m.tab(t).backToHome)
        assertFalse(
            m
                .spy(t)
                .state.value.canGoBack,
        )

        m.showHome(t)
        assertTrue(m.navigateBack(t))
        assertFalse(m.tab(t).isHome)
        assertEquals(a, m.tab(t).url)
        assertTrue(m.navigateBack(t))
        assertTrue(m.tab(t).isHome)
        assertFalse(m.navigateBack(t))
    }

    @Test
    fun ac5_repeatedBack_terminatesWithFalse_withoutRepeatingAState() {
        val m = manager()
        val t = m.selectedId()
        m.go(t, a)
        m.showHome(t)
        val seen = mutableListOf<Pair<Boolean, String>>()
        var result = true
        var steps = 0
        while (result && steps < 10) {
            result = m.navigateBack(t)
            steps++
            if (result) seen += m.tab(t).isHome to m.tab(t).url
        }
        assertFalse("Back must end with false within 10 presses", result)
        assertEquals("no state repeats: $seen", seen.size, seen.toSet().size)
        assertEquals(0, m.spy(t).goBackCalls)
    }
}
