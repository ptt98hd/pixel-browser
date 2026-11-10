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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Review spec 015 – đóng thanh tìm khi reload/toggle/engine reset; không đóng chỉ vì URL; ngoại lệ tạm. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelReviewTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val BrowserFixture.fake get() = selected().fake

    @Test
    fun reload_closesFindBar_andClearsOnce() {
        val f = BrowserFixture.onPage("https://a.com/")
        f.viewModel.onOpenFind()
        f.viewModel.onFindQueryChange("kot")
        f.viewModel.onReload()
        assertNull(f.state().find)
        assertEquals(1, f.fake.clearFindCalls)
    }

    @Test
    fun toggleDesktop_closesFindBar() {
        val f = BrowserFixture.onPage("https://a.com/")
        f.viewModel.onOpenFind()
        f.viewModel.onFindQueryChange("kot")
        f.viewModel.onToggleDesktopSite()
        assertNull(f.state().find)
    }

    @Test
    fun engineResetOfNonEmptyQuery_closesBar() {
        val f = BrowserFixture.onPage("https://a.com/")
        f.viewModel.onOpenFind()
        f.viewModel.onFindQueryChange("kot")
        f.fake.simulatePageStarted("https://a.com/")
        assertNull(f.state().find)
    }

    @Test
    fun urlDifferenceAlone_doesNotCloseBar() {
        val f = BrowserFixture.onPage("https://a.com/")
        f.viewModel.onOpenFind()
        f.fake.simulatePageFinished("https://a.com/redirected")
        assertNotNull(f.state().find)
        assertEquals(0, f.fake.clearFindCalls)
    }

    @Test
    fun longQuery_truncatedByEngine_keepsBarOpen() {
        val f = BrowserFixture.onPage("https://a.com/")
        f.viewModel.onOpenFind()
        f.viewModel.onFindQueryChange("x".repeat(300))
        assertNotNull(f.state().find)
    }

    @Test
    fun regularTab_toggle_thenClearingOverrides_returnsToDefaultOnNextNavigation() {
        val persisted = mutableListOf<DesktopSiteData>()
        val policy = DesktopSitePolicy(DesktopSiteData()) { persisted += it }
        val store = InMemoryStore()
        store.stored = TabsSnapshot(listOf(TabSnapshot("page", "https://a.com/")), "page")
        val tabManager = TabManager(SpySessionFactory(), store)
        val vm = BrowserViewModel(tabManager, desktopSites = policy)
        val fake = tabManager.selectedSpy().fake
        fake.setDesktopSiteResolver { policy.resolve(it) }
        fake.simulatePageFinished("https://a.com/")
        vm.onToggleDesktopSite()
        assertTrue(fake.state.value.isDesktopSite)
        policy.clearOverrides()
        fake.loadUrl("https://a.com/next")
        assertFalse(fake.state.value.isDesktopSite)
    }
}
