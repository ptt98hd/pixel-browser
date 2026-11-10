package app.pixel.browser

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.data.site.DesktopSiteData
import app.pixel.browser.core.data.site.FileDesktopSiteStore
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.settings.SettingsTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Spec 015 AC22 (mục Cài đặt mở được) và AC23 – nối `DesktopSitePolicy` vào factory session của app, với
 * `FakeEngineSession` (`bindBaseSessionFactory`) và `FileDesktopSiteStore` tạm (`bindDesktopSiteStore`).
 * Tab ẩn danh: app chưa có lối mở tab ẩn danh nên phần đó chỉ kiểm ở `BrowserViewModelDesktopTest` (AC20).
 */
@RunWith(RobolectricTestRunner::class)
// Màn hình Robolectric mặc định (320x470dp) không chứa hết các mục Cài đặt.
@Config(qualifiers = "w411dp-h891dp")
class MainActivityDesktopTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var file: File
    private lateinit var store: FileDesktopSiteStore

    @Before
    fun bind() {
        file = File(tmp.root, "site/desktop.json")
        store = FileDesktopSiteStore(file)
        app.bindHistoryStore(InMemoryHistoryStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindBookmarkRepository(InMemoryBookmarkRepository())
        app.bindBaseSessionFactory { FakeEngineSession() }
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    /** Gắn store (đọc đồng bộ) với dữ liệu [data]; ghi chạy ngay trên thread gọi. */
    private fun seedStore(data: DesktopSiteData) {
        store.write(data)
        app.bindDesktopSiteStore(store, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            scenario = it
            rule.waitForIdle()
        }

    private fun vm(s: ActivityScenario<MainActivity>): BrowserViewModel {
        lateinit var vm: BrowserViewModel
        s.onActivity { vm = ViewModelProvider(it)[BrowserViewModel::class.java] }
        return vm
    }

    private fun onMain(
        s: ActivityScenario<MainActivity>,
        block: () -> Unit,
    ) {
        s.onActivity { block() }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun selected(vm: BrowserViewModel) = vm.selectedSession.value as FakeEngineSession

    // --- AC23 ---

    @Test
    fun ac23_storedSite_isDesktopImmediately_otherSiteIsMobile() {
        seedStore(DesktopSiteData(sites = mapOf("example.com" to true)))
        val s = launch()
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl("https://example.com/") }
        assertTrue(selected(vm).state.value.isDesktopSite)
        onMain(s) { vm.onOpenUrlInNewTab("https://other.com/") }
        assertFalse(selected(vm).state.value.isDesktopSite)
    }

    @Test
    fun ac23_restoredTab_ofStoredSite_isDesktopOnFirstLoad() {
        seedStore(DesktopSiteData(sites = mapOf("example.com" to true)))
        app.tabStore.write(TabsSnapshot(listOf(TabSnapshot("t1", "https://example.com/")), "t1"))
        val s = launch()
        val session = selected(vm(s))
        assertEquals("https://example.com/", session.state.value.url)
        assertTrue(session.state.value.isDesktopSite)
        assertEquals(0, session.reloadCount)
    }

    @Test
    fun ac23_globalDefault_appliesToNewNavigations_withoutReloadingOpenTabs() {
        seedStore(DesktopSiteData())
        val s = launch()
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl("https://a.com/") }
        val open = selected(vm)
        assertFalse(open.state.value.isDesktopSite)
        onMain(s) { app.desktopSites.setDefault(true) }
        assertFalse(open.state.value.isDesktopSite)
        assertEquals(0, open.reloadCount)
        onMain(s) { vm.onOpenUrlInNewTab("https://b.com/") }
        assertTrue(selected(vm).state.value.isDesktopSite)
    }

    @Test
    fun ac23_toggleOnRegularTab_writesFile_andNewTabOfSameSiteIsDesktop() {
        seedStore(DesktopSiteData())
        val s = launch()
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl("https://example.com/") }
        onMain(s) { selected(vm).simulatePageFinished("https://example.com/") }
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_DESKTOP_SITE)
        assertTrue(selected(vm).state.value.isDesktopSite)
        assertEquals(DesktopSiteData(sites = mapOf("example.com" to true)), store.read())
        onMain(s) { vm.onOpenUrlInNewTab("https://example.com/other") }
        assertTrue(selected(vm).state.value.isDesktopSite)
    }

    @Test
    fun ac23_popupSession_alsoHasResolver() {
        seedStore(DesktopSiteData(sites = mapOf("example.com" to true)))
        val s = launch()
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl("https://a.com/") }
        val opener = selected(vm)
        onMain(s) { opener.simulateCreateWindow(isUserGesture = true) }
        val popup = selected(vm)
        assertTrue("popup must be a new session", popup !== opener)
        onMain(s) { popup.loadUrl("https://example.com/") }
        assertTrue(popup.state.value.isDesktopSite)
    }

    // --- AC22 (mục trong Cài đặt) ---

    @Test
    fun ac22_settingsItemDesktopSite_opensRealPage_placeholderGone() {
        seedStore(DesktopSiteData())
        launch()
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_SETTINGS)
        rule.onNodeWithTag(SettingsTestTags.placeholder("desktop_site")).assertDoesNotExist()
        tap(SettingsTestTags.item("desktop_site"))
        rule.onNodeWithTag(SettingsTestTags.page("desktop_site")).assertExists()
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).assertExists()
    }
}
