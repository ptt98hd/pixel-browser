package app.pixel.browser

import android.app.NotificationManager
import android.content.Context
import android.content.MutableContextWrapper
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.data.privacy.FakePrivacySettingsStore
import app.pixel.browser.core.data.privacy.PrivacySettings
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.FakeProfileGateway
import app.pixel.browser.core.engine.PrivateSpace
import app.pixel.browser.core.engine.ProfileGateway
import app.pixel.browser.core.engine.RetryScheduler
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Spec 016 AC16 (`FLAG_SECURE`, icon thanh hệ thống) và AC24 (nối ứng dụng: menu → profile → dọn khi hết tab ẩn
 * danh; cold start quét profile cũ; `onStop` không ghi tab ẩn danh). `FakeProfileGateway` + `PrivateSpace` thật.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class MainActivityPrivateTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    private val gateway = FakeProfileGateway()
    private var nameCounter = 0

    /** Sự kiện theo thứ tự thật (delete kèm trạng thái đóng của mọi session ẩn danh tại thời điểm gọi). */
    private val events = mutableListOf<String>()
    private val sessions = mutableListOf<EngineSession>()

    private val orderedGateway =
        object : ProfileGateway by gateway {
            override fun delete(profileName: String): Boolean {
                val privateClosed =
                    sessions.filter { it.isPrivate }.all {
                        (it as? WebViewEngineSession)?.isClosed ==
                            true
                    }
                events += "delete:$profileName:sessionsClosed=$privateClosed"
                return gateway.delete(profileName)
            }
        }

    @Before
    fun bind() {
        app.bindHistoryStore(InMemoryHistoryStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindBookmarkRepository(InMemoryBookmarkRepository())
        app.bindPrivacyStore(FakePrivacySettingsStore(), CoroutineScope(Dispatchers.Unconfined))
        bindSpace(orderedGateway)
        app.bindBaseSessionFactory { profile ->
            WebViewEngineSession(MutableContextWrapper(app), profile, orderedGateway).also { sessions += it }
        }
    }

    private fun bindSpace(g: ProfileGateway) {
        app.bindPrivateSpace(
            PrivateSpace(g, newName = { "private_t${++nameCounter}" }, scheduler = RetryScheduler { _, _ -> }),
        )
    }

    /** Dùng session Fake (không WebView) cho các test chỉ quan tâm tới UI/cờ cửa sổ. */
    private fun useFakeSessions() {
        app.bindBaseSessionFactory { profile -> FakeEngineSession(isPrivate = profile != null) }
    }

    @After
    fun closeScenario() {
        scenario?.close()
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

    private fun isSecure(s: ActivityScenario<MainActivity>): Boolean {
        var secure = false
        s.onActivity { secure = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
        return secure
    }

    private fun drainIo() = runBlocking { app.ioScope.launch {}.join() }

    private fun openPrivateFromMenu() {
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_NEW_PRIVATE_TAB)
    }

    // --- AC24: nối ứng dụng ---

    @Test
    fun ac24_menuNewPrivateTab_appliesExactlyOneProfileNamedPrivate() {
        launch()
        openPrivateFromMenu()
        assertEquals(1, gateway.appliedProfiles.size)
        assertTrue(gateway.appliedProfiles.single().startsWith("private_"))
    }

    @Test
    fun ac24_menuNewPrivateTab_whenSpaceUnavailable_isDisabled_andCreatesNothing() {
        bindSpace(FakeProfileGateway(supported = false))
        val s = launch()
        tap(BrowserTestTags.BTN_MENU)
        rule.onNodeWithTag(BrowserTestTags.MENU_NEW_PRIVATE_TAB).assertExists()
        assertFalse(vm(s).uiState.value.canOpenPrivate)
        assertEquals(emptyList<String>(), gateway.appliedProfiles)
    }

    @Test
    fun ac24_secondPrivateTab_sharesTheSameProfile() {
        val s = launch()
        openPrivateFromMenu()
        onMain(s) { vm(s).onNewTab() }
        assertEquals(2, gateway.appliedProfiles.size)
        assertEquals(1, gateway.appliedProfiles.toSet().size)
    }

    @Test
    fun ac24_closingAllPrivateTabs_deletesProfileOnce_afterSessionsClosed_thenReopenUsesNewName() {
        val s = launch()
        val vm = vm(s)
        openPrivateFromMenu()
        onMain(s) { vm.onNewTab() }
        val name = gateway.appliedProfiles.first()
        val privateIds =
            vm.uiState.value.tabs
                .filter { it.isPrivate }
                .map { it.id }
        assertEquals(2, privateIds.size)

        onMain(s) { vm.onCloseTab(privateIds[0]) }
        assertEquals("profile must stay while a private tab remains", emptyList<String>(), gateway.deleted)

        onMain(s) { vm.onCloseTab(privateIds[1]) }
        assertEquals(listOf("delete:$name:sessionsClosed=true"), events)
        assertEquals(listOf(name), gateway.deleted)

        openPrivateFromMenu()
        assertNotEquals(name, gateway.appliedProfiles.last())
        assertTrue(gateway.appliedProfiles.last().startsWith("private_"))
    }

    @Test
    fun ac24_closePrivateTabsAction_alsoWipesProfile() {
        val s = launch()
        val vm = vm(s)
        openPrivateFromMenu()
        val name = gateway.appliedProfiles.single()
        onMain(s) { vm.onClosePrivateTabs() }
        assertEquals(listOf(name), gateway.deleted)
    }

    @Test
    fun ac24_coldStart_leftoverProfiles_areSweptAfterFirstDraw_andNotAgainByFirstPrivateTab() {
        val leftover = FakeProfileGateway(names = listOf("Default", "private_old", "work"))
        bindSpace(leftover)
        app.bindBaseSessionFactory { profile -> WebViewEngineSession(MutableContextWrapper(app), profile, leftover) }
        launch()
        // Review 016: quét độc lập, khi rảnh sau khung hình đầu (không cần mở tab ẩn danh).
        assertEquals(listOf("private_old"), leftover.deleted)
        openPrivateFromMenu()
        assertEquals(listOf("private_old"), leftover.deleted)
        assertTrue("Default" in leftover.names && "work" in leftover.names)
    }

    @Test
    fun ac24_onStop_keepsPrivateTabsInMemory_butTabsJsonHasNone() {
        val s = launch()
        val vm = vm(s)
        openPrivateFromMenu()
        onMain(s) { vm.onOpenUrl("https://secret-private.example/page") }

        s.moveToState(Lifecycle.State.CREATED) // onStop
        drainIo()

        val stored = app.tabStore.read()
        assertTrue("tabs.json must have been written", stored != null)
        assertTrue(stored!!.tabs.none { it.url.contains("secret-private") })
        assertTrue(
            vm.uiState.value.tabs
                .any { it.isPrivate },
        )
        s.moveToState(Lifecycle.State.RESUMED)
        assertTrue(vm.uiState.value.selectedIsPrivate)
    }

    @Test
    fun ac26_privateFlow_postsNoNotification_andCreatesNoChannel() {
        val s = launch()
        val vm = vm(s)
        openPrivateFromMenu()
        onMain(s) { vm.onClosePrivateTabs() }
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertEquals(0, shadowOf(nm).size())
        assertTrue(shadowOf(nm).notificationChannels.isEmpty())
    }

    // --- AC16: FLAG_SECURE + icon thanh hệ thống ---

    @Test
    fun ac16_selectingPrivateTab_setsFlagSecure_selectingNormalTabClearsIt() {
        useFakeSessions()
        val s = launch()
        val vm = vm(s)
        val normalId = vm.uiState.value.selectedTabId!!
        assertFalse(isSecure(s))

        openPrivateFromMenu()
        assertTrue(isSecure(s))

        onMain(s) { vm.onSelectTab(normalId) }
        assertFalse(isSecure(s))
    }

    @Test
    fun ac16_screenshotSettingOff_neverSetsFlag() {
        useFakeSessions()
        app.bindPrivacyStore(
            FakePrivacySettingsStore(PrivacySettings(blockPrivateScreenshots = false)),
            CoroutineScope(Dispatchers.Unconfined),
        )
        val s = launch()
        openPrivateFromMenu()
        assertTrue(vm(s).uiState.value.selectedIsPrivate)
        assertFalse(isSecure(s))
    }

    @Test
    fun ac16_switcherShowingPrivateGroup_fromNormalTab_setsFlag() {
        useFakeSessions()
        val s = launch()
        val vm = vm(s)
        val normalId = vm.uiState.value.selectedTabId!!
        openPrivateFromMenu()
        onMain(s) { vm.onSelectTab(normalId) }
        assertFalse(isSecure(s))

        tap(BrowserTestTags.BTN_TABS)
        assertFalse("normal group does not need the flag", isSecure(s))
        tap(BrowserTestTags.TAB_SWITCHER_GROUP_PRIVATE)
        assertTrue(isSecure(s))
        tap(BrowserTestTags.TAB_SWITCHER_GROUP_NORMAL)
        assertFalse(isSecure(s))
    }

    @Test
    fun ac16_flagIsReappliedAfterActivityRecreate() {
        useFakeSessions()
        val s = launch()
        openPrivateFromMenu()
        assertTrue(isSecure(s))
        s.recreate()
        rule.waitForIdle()
        assertTrue(isSecure(s))
        assertTrue(vm(s).uiState.value.selectedIsPrivate)
    }

    @Test
    fun ac21_systemBars_useLightIcons_whenPrivateTabSelected() {
        useFakeSessions()
        val s = launch()
        openPrivateFromMenu()
        var lightStatus = true
        var lightNav = true
        s.onActivity {
            val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
            lightStatus = controller.isAppearanceLightStatusBars
            lightNav = controller.isAppearanceLightNavigationBars
        }
        assertFalse("status bar icons must be light on the dark private theme", lightStatus)
        assertFalse("navigation bar icons must be light on the dark private theme", lightNav)
    }
}
