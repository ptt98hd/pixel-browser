package app.pixel.browser.feature.browser

import app.pixel.browser.core.data.site.DesktopSiteData
import app.pixel.browser.core.data.site.DesktopSitePolicy
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 015 AC20 – ViewModel bật/tắt trang web máy tính (JVM, `FakeEngineSession` + `DesktopSitePolicy` thật). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelDesktopTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val persisted = mutableListOf<DesktopSiteData>()
    private val policy = DesktopSitePolicy(DesktopSiteData()) { persisted += it }

    private class Env(
        val tabManager: TabManager,
        val viewModel: BrowserViewModel,
    ) {
        fun state() = viewModel.uiState.value

        fun selected() = tabManager.selectedSpy()
    }

    private fun env(
        url: String?,
        desktopSites: DesktopSitePolicy? = policy,
    ): Env {
        val store = InMemoryStore()
        if (url != null) store.stored = TabsSnapshot(listOf(TabSnapshot("page", url)), "page")
        val tabManager = TabManager(SpySessionFactory(), store)
        val env = Env(tabManager, BrowserViewModel(tabManager, desktopSites = desktopSites))
        if (url != null) env.selected().fake.simulatePageFinished(url)
        return env
    }

    private val page = "https://a.com/"

    @Test
    fun ac20_webTab_startsMobile_andCanToggle() {
        val e = env(page)
        assertFalse(e.state().isDesktopSite)
        assertTrue(e.state().canToggleDesktopSite)
    }

    @Test
    fun ac20_toggleOn_updatesSession_andSavesSiteOverrideOnce() {
        val e = env(page)
        e.viewModel.onToggleDesktopSite()
        assertTrue(
            e
                .selected()
                .fake.state.value.isDesktopSite,
        )
        assertEquals(1, e.selected().fake.reloadCount)
        assertTrue(e.state().isDesktopSite)
        assertEquals(listOf(DesktopSiteData(sites = mapOf("a.com" to true))), persisted)
        assertEquals(true, policy.overrideFor("a.com"))
    }

    @Test
    fun ac20_toggleOff_savesFalse_whichRemovesOverrideWhenEqualToDefault() {
        val e = env(page)
        e.viewModel.onToggleDesktopSite()
        e.viewModel.onToggleDesktopSite()
        assertFalse(e.state().isDesktopSite)
        assertEquals(2, persisted.size)
        assertEquals(DesktopSiteData(), persisted.last())
        assertNull(policy.overrideFor("a.com"))
    }

    @Test
    fun ac20_privateTab_changesSession_butNeverTouchesPolicy() {
        val e = env(null)
        e.tabManager.openTab(page, isPrivate = true)
        e.selected().visit(page)
        assertTrue(e.state().canToggleDesktopSite)
        e.viewModel.onToggleDesktopSite()
        assertTrue(
            e
                .selected()
                .fake.state.value.isDesktopSite,
        )
        assertTrue(e.state().isDesktopSite)
        assertTrue(persisted.isEmpty())
        assertNull(policy.overrideFor("a.com"))
    }

    @Test
    fun ac20_urlWithoutSiteKey_onlyChangesSession() {
        val e = env("https://:8080/x")
        assertTrue(e.state().canToggleDesktopSite)
        e.viewModel.onToggleDesktopSite()
        assertTrue(
            e
                .selected()
                .fake.state.value.isDesktopSite,
        )
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun ac20_atHome_cannotToggle_andToggleDoesNothing() {
        val e = env(null)
        assertTrue(e.state().isHome)
        assertFalse(e.state().canToggleDesktopSite)
        e.viewModel.onToggleDesktopSite()
        assertFalse(e.state().isDesktopSite)
        assertEquals(0, e.selected().fake.reloadCount)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun ac20_aboutBlank_cannotToggle_andToggleDoesNothing() {
        val e = env("about:blank")
        assertFalse(e.state().canToggleDesktopSite)
        e.viewModel.onToggleDesktopSite()
        assertFalse(e.state().isDesktopSite)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun ac20_eachTab_hasItsOwnDesktopState() {
        val e = env(page)
        val first = e.tabManager.selectedTabId.value!!
        e.viewModel.onToggleDesktopSite()
        val second = e.tabManager.openTab("https://b.com/").id
        e.selected().visit("https://b.com/")
        assertFalse(e.state().isDesktopSite)
        e.viewModel.onSelectTab(first)
        assertTrue(e.state().isDesktopSite)
        e.viewModel.onSelectTab(second)
        assertFalse(e.state().isDesktopSite)
    }

    @Test
    fun ac20_withoutPolicy_toggleStillChangesSession_butSavesNothing() {
        val e = env(page, desktopSites = null)
        e.viewModel.onToggleDesktopSite()
        assertTrue(e.state().isDesktopSite)
        assertTrue(
            e
                .selected()
                .fake.state.value.isDesktopSite,
        )
        assertTrue(persisted.isEmpty())
    }
}
