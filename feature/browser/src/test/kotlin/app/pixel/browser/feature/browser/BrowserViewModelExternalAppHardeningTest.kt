package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.intent.ExternalTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 006 – review hardening của AC23–AC25 (JVM, launcher giả). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelExternalAppHardeningTest {
    private lateinit var f: BrowserFixture
    private val vm get() = f.viewModel
    private val launcher get() = f.launcher

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
        f.selected().resetCalls()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun SpySession.emit(
        uri: String,
        gesture: Boolean,
    ) {
        assertTrue(fake.simulateNavigation(uri, isMainFrame = true, hasUserGesture = gesture))
    }

    private fun selectedId(): String = f.tabManager.selectedTabId.value!!

    // --- Mô tả từ cùng parse với launcher ---

    @Test
    fun undescribableRequest_isNotPrompted_isDismissed_andShowsBlockedMessage() {
        launcher.describeResult = null
        val s = f.selected()
        s.emit("intent://x#Intent;scheme=evil;end", gesture = false)
        assertNull(f.state().externalPrompt)
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[1L])
        assertEquals(BrowserMessages.EXTERNAL_APP_BLOCKED, f.state().message)
        assertTrue(launcher.calls.isEmpty())
    }

    @Test
    fun undescribableRequest_withGesture_isNotLaunched() {
        launcher.describeResult = null
        val s = f.selected()
        s.emit("intent://x#Intent;scheme=evil;end", gesture = true)
        assertTrue(launcher.calls.isEmpty())
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[1L])
        assertEquals(BrowserMessages.EXTERNAL_APP_BLOCKED, f.state().message)
    }

    @Test
    fun prompt_exposesOwningTabId() {
        launcher.describeResult = ExternalTarget(scheme = "zxing", packageName = "com.x")
        f.selected().emit("intent://scan/#Intent;scheme=zxing;package=com.x;end", gesture = false)
        assertNotNull(f.state().externalPrompt)
        assertEquals(selectedId(), f.state().externalPromptTabId)
    }

    // --- Bẫy hộp thoại lặp ---

    @Test
    fun afterCancel_furtherNoGestureRequests_areSuppressed_untilNextPageLoad() {
        val s = f.selected()
        val tab = selectedId()
        s.emit("tel:1", gesture = false)
        vm.onExternalAppDecision(tab, 1L, open = false)

        s.emit("tel:2", gesture = false)
        assertNull(f.state().externalPrompt)
        assertEquals(EngineResponse.Dismiss(suppressFurther = true), s.fake.responses[2L])

        s.visit("https://other.com/")
        s.emit("tel:3", gesture = false)
        assertEquals("tel:3", f.state().externalPrompt?.uri)
    }

    @Test
    fun afterCancel_gestureRequest_liftsSuppression_andLaunches() {
        val s = f.selected()
        val tab = selectedId()
        s.emit("tel:1", gesture = false)
        vm.onExternalAppDecision(tab, 1L, open = false)
        s.emit("tel:2", gesture = true)
        assertEquals(listOf("tel:2"), launcher.calls)
        s.emit("tel:3", gesture = false)
        assertEquals("tel:3", f.state().externalPrompt?.uri)
    }

    // --- Một chạm mở nhiều app ---

    @Test
    fun twoGestureRequestsInARow_launchOnlyOnce_secondGoesToDialog() {
        val s = f.selected()
        s.emit("tel:1", gesture = true)
        s.emit("tel:2", gesture = true)
        assertEquals(listOf("tel:1"), launcher.calls)
        assertEquals("tel:2", f.state().externalPrompt?.uri)
    }

    @Test
    fun gestureRequest_afterThrottleWindow_launchesDirectly() {
        var now = 10_000L
        f = BrowserFixture(clock = { now })
        f.selected().visit("https://a.com/")
        val s = f.selected()
        s.emit("tel:1", gesture = true)
        now += 1_500
        s.emit("tel:2", gesture = true)
        assertEquals(listOf("tel:1", "tel:2"), f.launcher.calls)
        assertNull(f.state().externalPrompt)
    }

    @Test
    fun gestureRequest_whileAppInBackground_goesToDialog() {
        vm.onAppVisibilityChanged(visible = false)
        f.selected().emit("tel:1", gesture = true)
        assertTrue(launcher.calls.isEmpty())
        assertEquals("tel:1", f.state().externalPrompt?.uri)
    }

    // --- Quyết định gắn với tab ---

    @Test
    fun decision_forTabNoLongerHoldingRequest_doesNothingToOtherTab() {
        val first = selectedId()
        f.selected().emit("tel:1", gesture = false)
        val prompt = f.state().externalPrompt!!

        // Tab nền B cũng có request id 1 trong hàng đợi.
        val b = f.tabManager.openTab("https://b.com", select = false)
        val bSpy = f.tabManager.spy(b.id)
        bSpy.fake.simulatePageFinished("https://b.com")
        assertTrue(bSpy.fake.simulateNavigation("tel:999", hasUserGesture = false))
        vm.onSelectTab(b.id)
        assertEquals("tel:999", f.state().externalPrompt?.uri)

        // Quyết định trễ cho hộp thoại của tab đầu (đã trả lời ở nơi khác) không được chạm request của B.
        f.tabManager
            .spy(first)
            .fake
            .respond(prompt.id, EngineResponse.Dismiss())
        vm.onExternalAppDecision(first, prompt.id, open = true)
        assertTrue(launcher.calls.isEmpty())
        assertTrue(bSpy.fake.responses.isEmpty())
        assertEquals("tel:999", f.state().externalPrompt?.uri)
    }

    @Test
    fun decision_targetsGivenTab_evenIfAnotherTabIsSelected() {
        val first = selectedId()
        f.selected().emit("tel:1", gesture = false)
        val b = f.tabManager.openTab("https://b.com", select = false)
        vm.onSelectTab(b.id)
        vm.onExternalAppDecision(first, 1L, open = false)
        assertEquals(
            EngineResponse.Dismiss(),
            f.tabManager
                .spy(first)
                .fake.responses[1L],
        )
        assertNotNull(f.tabManager.session(b.id))
        assertTrue(launcher.calls.isEmpty())
    }
}
