package app.pixel.browser.feature.browser

import app.pixel.browser.core.data.privacy.PrivateAutoClose
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.SitePermission.Camera
import app.pixel.browser.feature.browser.intent.IncomingRequest
import app.pixel.browser.feature.browser.web.SitePermissionMemory
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

/** Spec 016 AC12, AC13, AC15 (phần ViewModel), AC19–AC20 (trạng thái) – JVM với `SpySession`/Fake. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelPrivateTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val a = "https://a.com/"
    private val b = "https://b.com/"

    private var available = true
    private val thumbs = FakeTabThumbnailRepository()
    private val visits = FakePageVisitRecorder()

    private fun onPage(
        autoClose: PrivateAutoCloseGuard = PrivateAutoCloseGuard({ 0L }, { PrivateAutoClose.Never }),
        privateMemory: SitePermissionMemory = SitePermissionMemory(),
    ) = BrowserFixture
        .onPage(
            a,
            thumbnails = thumbs,
            pageVisits = visits,
            privateMemory = privateMemory,
            privateAvailable = { available },
            autoCloseGuard = autoClose,
        ).also { thumbs.calls.clear() }

    private fun BrowserFixture.privateTabs() = state().tabs.filter { it.isPrivate }

    private fun BrowserFixture.normalTabs() = state().tabs.filterNot { it.isPrivate }

    // --- AC12: mở tab ---

    @Test
    fun ac12_newPrivateTab_whenAvailable_selectsPrivateHomeTab() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        val state = f.state()
        assertTrue(state.selectedIsPrivate)
        assertTrue(state.isHome)
        assertEquals(2, state.tabs.size)
        assertEquals(1, f.privateTabs().size)
        assertEquals(state.selectedTabId, f.privateTabs().single().id)
        assertEquals(true, f.factory.requestedPrivate.last())
    }

    @Test
    fun ac12_newPrivateTab_whenUnavailable_createsNothing_andExplains() {
        available = false
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        val state = f.state()
        assertEquals(1, state.tabs.size)
        assertFalse(state.selectedIsPrivate)
        assertEquals("private_unavailable", state.message)
        assertEquals(BrowserMessages.PRIVATE_UNAVAILABLE, state.message)
        assertTrue(f.factory.requestedPrivate.none { it })
    }

    @Test
    fun ac12_canOpenPrivate_reflectsAvailability_afterResolve() {
        val f = onPage()
        f.viewModel.onResolvePrivateAvailability()
        assertTrue(f.state().canOpenPrivate)
        available = false
        f.viewModel.onResolvePrivateAvailability()
        assertFalse(f.state().canOpenPrivate)
    }

    @Test
    fun ac12_canOpenPrivate_isFalse_whenUnavailable() {
        val f = BrowserFixture.onPage(a, privateAvailable = { false })
        f.viewModel.onResolvePrivateAvailability()
        assertFalse(f.state().canOpenPrivate)
    }

    @Test
    fun review_availabilityQuery_isLazy_notOnStartupOrOrdinaryActions() {
        var queries = 0
        val f = BrowserFixture.onPage(a, privateAvailable = { true.also { queries++ } })
        f.viewModel.onNewTab()
        f.viewModel.onOpenUrl(b)
        f.viewModel.onOpenTabSwitcher()
        f.viewModel.onMessageShown()
        assertEquals("no MULTI_PROFILE query before the menu/switcher asks", 0, queries)
        assertFalse(f.state().canOpenPrivate)

        f.viewModel.onResolvePrivateAvailability()
        assertEquals(1, queries)
        assertTrue(f.state().canOpenPrivate)
    }

    @Test
    fun review_newPrivateTab_resolvesAvailability_soEntryStateIsCorrectAfterwards() {
        available = false
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        assertFalse(f.state().canOpenPrivate)
    }

    @Test
    fun ac12_newTab_fromPrivateTab_isPrivate() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onNewTab()
        assertEquals(2, f.privateTabs().size)
        assertTrue(f.state().selectedIsPrivate)
        assertEquals(1, f.normalTabs().size)
    }

    @Test
    fun ac12_newTab_fromNormalTab_isNormal() {
        val f = onPage()
        f.viewModel.onNewTab()
        assertFalse(f.state().selectedIsPrivate)
        assertEquals(0, f.privateTabs().size)
        assertEquals(2, f.normalTabs().size)
    }

    @Test
    fun ac12_openUrlInNewTab_inheritsGroupOfSelectedTab() {
        val f = onPage()
        f.viewModel.onOpenUrlInNewTab(b)
        assertFalse("from a normal tab", f.state().selectedIsPrivate)

        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrlInNewTab("https://c.com/")
        assertTrue("from a private tab", f.state().selectedIsPrivate)
        assertEquals(2, f.privateTabs().size)
    }

    @Test
    fun ac12_incomingOpenUrl_fromPrivateHomeTab_neverReusesPrivateTab_createsNormalTab() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        val privateId = f.state().selectedTabId!!
        val privateSession = f.tabManager.spy(privateId)

        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl(b))

        val state = f.state()
        assertFalse("external links always open a normal tab", state.selectedIsPrivate)
        assertEquals(b, state.url)
        assertTrue(privateSession.loadUrlCalls.isEmpty())
        val stillPrivateHome = state.tabs.single { it.id == privateId }
        assertTrue(stillPrivateHome.isPrivate)
        assertTrue(stillPrivateHome.isHome)
    }

    @Test
    fun ac12_incomingOpenUrl_fromPrivatePage_createsNormalTab() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrl("https://secret.example/")
        f.selected().fake.simulatePageFinished("https://secret.example/")

        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl(b))

        assertFalse(f.state().selectedIsPrivate)
        assertEquals(b, f.state().url)
        assertEquals(1, f.privateTabs().size)
    }

    // --- AC13: không lưu ---

    @Test
    fun ac13_privatePageFinished_isRecordedAsPrivateVisit() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrl(b)
        f.selected().fake.simulatePageFinished(b)
        // onPage() đã ghi sẵn một lượt truy cập thường vào cùng recorder.
        val visit = visits.visits.filter { it.isPrivate }.single()
        assertTrue(visit.isPrivate)
        assertEquals(b, visit.url)
    }

    @Test
    fun ac13_leavingPrivatePage_capturesThumbnailWithoutPersisting() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrl(b)
        f.selected().fake.simulatePageFinished(b)
        thumbs.calls.clear()

        f.viewModel.onOpenTabSwitcher()

        val capture = thumbs.captures.single()
        assertFalse(capture.persist)
    }

    @Test
    fun ac13_lastPrivateTabClosed_purgesPrivateThumbnails_andClearsPrivateMemory() {
        val privateMemory = SitePermissionMemory().apply { set("https://a.com", Camera, true) }
        val f = onPage(privateMemory = privateMemory)
        f.viewModel.onNewPrivateTab()
        val p1 = f.state().selectedTabId!!
        f.viewModel.onOpenUrl(b)
        f.viewModel.onNewTab()
        val p2 = f.state().selectedTabId!!
        thumbs.calls.clear()

        f.viewModel.onCloseTab(p1)
        assertTrue("not the last private tab yet", thumbs.purges.isEmpty())
        assertEquals(true, privateMemory.get("https://a.com", Camera))

        f.viewModel.onCloseTab(p2)

        assertEquals(1, f.privateEmptyCalls)
        assertEquals(setOf(p1, p2), thumbs.purges.single().tabIds)
        assertNull(privateMemory.get("https://a.com", Camera))
    }

    @Test
    fun ac13_closePrivateTabs_purgesThumbnails_andClearsPrivateMemory_leavesNormalMemory() {
        val privateMemory = SitePermissionMemory().apply { set("https://a.com", Camera, true) }
        val f = onPage(privateMemory = privateMemory)
        f.permissionMemory.set("https://a.com", Camera, true)
        f.viewModel.onNewPrivateTab()
        val p1 = f.state().selectedTabId!!
        thumbs.calls.clear()

        f.viewModel.onClosePrivateTabs()

        assertEquals(setOf(p1), thumbs.purges.single().tabIds)
        assertNull(privateMemory.get("https://a.com", Camera))
        assertEquals(true, f.permissionMemory.get("https://a.com", Camera))
    }

    @Test
    fun ac13_closingNormalTabs_neverPurges() {
        val f = onPage()
        f.viewModel.onNewTab()
        f.viewModel.onCloseTab(f.state().selectedTabId!!)
        assertTrue(thumbs.purges.isEmpty())
    }

    @Test
    fun ac13_privateTabPermission_isRememberedInPrivateMemoryOnly() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrl(a)
        f.selected().fake.simulatePageFinished(a)

        f.selected().fake.simulatePermissionRequest(setOf(Camera))
        val prompt = f.state().pendingRequest as EngineRequest.PermissionPrompt
        f.viewModel.onPermissionDecision(prompt.id, allow = true)

        assertEquals(true, f.privateMemory.get("https://a.com", Camera))
        assertNull("normal memory must stay untouched", f.permissionMemory.get("https://a.com", Camera))
    }

    @Test
    fun ac13_privateAllow_doesNotAutoGrantNormalTab() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        val privateId = f.state().selectedTabId!!
        f.viewModel.onOpenUrl(a)
        f.selected().fake.simulatePageFinished(a)
        f.selected().fake.simulatePermissionRequest(setOf(Camera))
        val prompt = f.state().pendingRequest as EngineRequest.PermissionPrompt
        f.viewModel.onPermissionDecision(prompt.id, allow = true)

        f.viewModel.onSelectTab(BrowserFixture.PAGE_TAB_ID)
        f.selected().fake.simulatePermissionRequest(setOf(Camera))

        assertNotNull("normal tab must still ask", f.state().pendingRequest as? EngineRequest.PermissionPrompt)
        assertNotNull(f.tabManager.session(privateId))
    }

    @Test
    fun ac13_normalAllow_doesNotAutoGrantPrivateTab() {
        val f = onPage()
        f.selected().fake.simulatePermissionRequest(setOf(Camera))
        val prompt = f.state().pendingRequest as EngineRequest.PermissionPrompt
        f.viewModel.onPermissionDecision(prompt.id, allow = true)
        assertEquals(true, f.permissionMemory.get("https://a.com", Camera))

        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrl(a)
        f.selected().fake.simulatePageFinished(a)
        f.selected().fake.simulatePermissionRequest(setOf(Camera))

        assertNotNull("private tab must still ask", f.state().pendingRequest as? EngineRequest.PermissionPrompt)
        assertNull(f.privateMemory.get("https://a.com", Camera))
    }

    // --- AC15: tự đóng khi quay lại ---

    @Test
    fun ac15_foregroundReturn_whenGuardSaysClose_closesPrivateTabsOnly() {
        var now = 0L
        val guard = PrivateAutoCloseGuard({ now }, { PrivateAutoClose.Minutes5 })
        val f = onPage(autoClose = guard)
        f.viewModel.onNewPrivateTab()
        f.viewModel.onNewTab()
        assertEquals(2, f.privateTabs().size)

        guard.onBackground(hasPrivateTabs = true, isChangingConfigurations = false)
        now += 5 * 60_000L
        f.viewModel.onForegroundReturn()

        assertEquals(0, f.privateTabs().size)
        assertEquals(listOf(BrowserFixture.PAGE_TAB_ID), f.normalTabs().map { it.id })
        assertFalse(f.state().selectedIsPrivate)
        assertEquals(BrowserFixture.PAGE_TAB_ID, f.state().selectedTabId)
    }

    @Test
    fun ac15_foregroundReturn_beforeDeadline_keepsPrivateTabs() {
        var now = 0L
        val guard = PrivateAutoCloseGuard({ now }, { PrivateAutoClose.Minutes5 })
        val f = onPage(autoClose = guard)
        f.viewModel.onNewPrivateTab()

        guard.onBackground(hasPrivateTabs = true, isChangingConfigurations = false)
        now += 4 * 60_000L
        f.viewModel.onForegroundReturn()

        assertEquals(1, f.privateTabs().size)
        assertTrue(f.state().selectedIsPrivate)
    }

    @Test
    fun ac15_foregroundReturn_withDefaultNeverGuard_doesNothing() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onForegroundReturn()
        assertEquals(1, f.privateTabs().size)
    }

    // --- AC19: tab_count ---

    @Test
    fun ac19_tabCount_countsOnlyTheGroupOfTheSelectedTab() {
        val f = onPage()
        f.viewModel.onNewTab() // 2 tab thường
        f.viewModel.onNewPrivateTab() // 1 tab ẩn danh, đang chọn
        assertEquals(1, f.state().tabCount)
        f.viewModel.onSelectTab(BrowserFixture.PAGE_TAB_ID)
        assertEquals(2, f.state().tabCount)
    }

    @Test
    fun ac19_tabsList_stillContainsAllGroups_forTheSwitcher() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        assertEquals(2, f.state().tabs.size)
    }

    // --- AC19–AC20: trạng thái cho trình chuyển tab ---

    @Test
    fun ac19_switcherGroupChange_isReflectedInState() {
        val f = onPage()
        assertFalse(f.state().switcherShowsPrivate)
        f.viewModel.onSwitcherGroupChange(true)
        assertTrue(f.state().switcherShowsPrivate)
        f.viewModel.onSwitcherGroupChange(false)
        assertFalse(f.state().switcherShowsPrivate)
    }

    @Test
    fun ac20_closePrivateTabs_closesAll_andIsNotUndoable() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onNewTab()
        f.viewModel.onClosePrivateTabs()

        assertEquals(0, f.privateTabs().size)
        assertFalse(f.state().lastCloseUndoable)
        f.viewModel.onUndoCloseTab()
        assertEquals("undo must never bring a private tab back", 0, f.privateTabs().size)
    }

    @Test
    fun ac20_closingLastPrivateTabWithX_isNotUndoable() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onCloseTab(f.state().selectedTabId!!)
        assertFalse(f.state().lastCloseUndoable)
    }

    @Test
    fun ac20_closingNonLastPrivateTab_isUndoable() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        val first = f.state().selectedTabId!!
        f.viewModel.onNewTab()
        f.viewModel.onCloseTab(first)
        assertTrue(f.state().lastCloseUndoable)
        f.viewModel.onUndoCloseTab()
        assertEquals(2, f.privateTabs().size)
    }

    @Test
    fun ac20_closingNormalTab_isUndoable() {
        val f = onPage()
        f.viewModel.onNewTab()
        f.viewModel.onCloseTab(f.state().selectedTabId!!)
        assertTrue(f.state().lastCloseUndoable)
    }

    @Test
    fun ac20_closePrivateTabs_withoutPrivateTabs_changesNothing() {
        val f = onPage()
        f.viewModel.onClosePrivateTabs()
        assertEquals(1, f.state().tabs.size)
        assertEquals(0, f.privateEmptyCalls)
    }

    // --- AC11: onCleared ---

    @Test
    fun ac11_tabsSnapshot_neverContainsPrivateTabs_evenWhenSelected() {
        val f = onPage()
        f.viewModel.onNewPrivateTab()
        f.viewModel.onOpenUrl("https://secret-private.example/")
        val snapshot = f.viewModel.tabsSnapshot()
        assertTrue(snapshot.tabs.none { it.url.contains("secret-private") })
        assertEquals(BrowserFixture.PAGE_TAB_ID, snapshot.selectedTabId)
    }
}
