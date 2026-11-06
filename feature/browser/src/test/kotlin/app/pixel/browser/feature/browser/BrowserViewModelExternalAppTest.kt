package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.intent.ExternalLaunchResult
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

/** Spec 006 – AC23–AC25: ViewModel xử lý `EngineRequest.ExternalApp` (JVM, launcher giả). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelExternalAppTest {
    private lateinit var f: BrowserFixture
    private val vm get() = f.viewModel
    private val launcher get() = f.launcher

    private val zxingWithFallback =
        "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;" +
            "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fapp;end"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture.onPage("https://a.com/")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Trang trên [this] điều hướng tới [uri] (main frame); engine phải chặn và phát `ExternalApp`. */
    private fun SpySession.emit(
        uri: String,
        gesture: Boolean,
    ) {
        assertTrue(fake.simulateNavigation(uri, isMainFrame = true, hasUserGesture = gesture))
    }

    // --- AC23 ---

    @Test
    fun ac23_withGesture_launchesOnce_respondsDismiss_withoutPrompt() {
        val s = f.selected()
        s.emit("tel:123", gesture = true)
        assertEquals(listOf("tel:123"), launcher.calls)
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[1L])
        assertTrue(
            s.fake.requests.value
                .isEmpty(),
        )
        assertNull(f.state().externalPrompt)
        assertNull(f.state().message)
    }

    @Test
    fun ac23_notFoundWithFallback_loadsFallbackInSameTab() {
        launcher.result = ExternalLaunchResult.NotFound("https://example.com/app")
        val s = f.selected()
        s.emit(zxingWithFallback, gesture = true)
        assertEquals(listOf("https://example.com/app"), s.loadUrlCalls)
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[1L])
        assertEquals(1, f.state().tabCount)
    }

    @Test
    fun ac23_notFoundWithoutFallback_showsMessage_withoutLoad() {
        launcher.result = ExternalLaunchResult.NotFound(null)
        val s = f.selected()
        s.emit("market://details?id=x", gesture = true)
        assertTrue(s.loadUrlCalls.isEmpty())
        assertNotNull(f.state().message)
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[1L])
    }

    @Test
    fun ac23_rejected_showsMessage_withoutLoad() {
        launcher.result = ExternalLaunchResult.Rejected
        val s = f.selected()
        s.emit("intent://x#Intent;scheme=myapp;package=app.pixel.browser;end", gesture = true)
        assertTrue(s.loadUrlCalls.isEmpty())
        assertNotNull(f.state().message)
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[1L])
    }

    @Test
    fun ac23_openInBrowser_loadsUrlInSameTab() {
        launcher.result = ExternalLaunchResult.OpenInBrowser("https://example.com/a")
        val s = f.selected()
        s.emit("intent://example.com/a#Intent;scheme=https;end", gesture = true)
        assertEquals(listOf("https://example.com/a"), s.loadUrlCalls)
        assertNull(f.state().message)
    }

    // --- AC24 ---

    @Test
    fun ac24_withoutGesture_doesNotLaunch_andPromptsWithRequest() {
        val s = f.selected()
        s.emit("tel:123", gesture = false)
        assertTrue(launcher.calls.isEmpty())
        val prompt = f.state().externalPrompt
        assertNotNull(prompt)
        assertEquals("tel:123", prompt!!.uri)
        assertEquals(1L, prompt.id)
        assertEquals(
            s.fake.requests.value
                .single(),
            prompt,
        )
        assertTrue(s.fake.responses.isEmpty())
    }

    @Test
    fun ac24_open_launchesOnce_respondsDismiss_andClosesPrompt() {
        val s = f.selected()
        s.emit("tel:123", gesture = false)
        val id = f.state().externalPrompt!!.id
        vm.onExternalAppDecision(id, open = true)
        assertEquals(listOf("tel:123"), launcher.calls)
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[id])
        assertNull(f.state().externalPrompt)
        assertTrue(
            s.fake.requests.value
                .isEmpty(),
        )
    }

    @Test
    fun ac24_open_handlesResultLikeAc23() {
        launcher.result = ExternalLaunchResult.NotFound("https://example.com/app")
        val s = f.selected()
        s.emit(zxingWithFallback, gesture = false)
        vm.onExternalAppDecision(f.state().externalPrompt!!.id, open = true)
        assertEquals(listOf("https://example.com/app"), s.loadUrlCalls)
    }

    @Test
    fun ac24_cancel_doesNotLaunch_respondsDismiss_andClosesPrompt() {
        val s = f.selected()
        s.emit("tel:123", gesture = false)
        val id = f.state().externalPrompt!!.id
        vm.onExternalAppDecision(id, open = false)
        assertTrue(launcher.calls.isEmpty())
        assertEquals(EngineResponse.Dismiss(), s.fake.responses[id])
        assertNull(f.state().externalPrompt)
        assertTrue(s.loadUrlCalls.isEmpty())
    }

    @Test
    fun ac24_withoutGesture_fallbackIsNotLoadedAutomatically() {
        val s = f.selected()
        s.emit(zxingWithFallback, gesture = false)
        assertNotNull(f.state().externalPrompt)
        assertTrue(launcher.calls.isEmpty())
        assertTrue(s.loadUrlCalls.isEmpty())
    }

    // --- AC25 ---

    @Test
    fun ac25_spamWhilePromptShown_isDismissedImmediately_promptKeepsFirst() {
        val s = f.selected()
        s.emit("tel:1", gesture = false)
        val first = f.state().externalPrompt!!
        repeat(5) { i -> s.fake.simulateNavigation("tel:spam$i", hasUserGesture = false) }

        assertEquals(first, f.state().externalPrompt)
        assertTrue(launcher.calls.isEmpty())
        assertEquals(listOf(first), s.fake.requests.value)
        for (id in 2L..6L) assertEquals("request $id", EngineResponse.Dismiss(), s.fake.responses[id])
        assertNull(s.fake.responses[first.id])
    }

    @Test
    fun ac25_backgroundTabRequest_isNotPromptedUntilSelected_evenWithGesture() {
        val bg = f.tabManager.openTab("https://b.com", select = false)
        val bgSpy = f.tabManager.spy(bg.id)
        bgSpy.fake.simulatePageFinished("https://b.com")
        assertTrue(bgSpy.fake.simulateNavigation("tel:123", hasUserGesture = true))

        assertNull(f.state().externalPrompt)
        assertTrue(launcher.calls.isEmpty())
        assertEquals(1, bgSpy.fake.requests.value.size)
        assertTrue(bgSpy.fake.responses.isEmpty())

        vm.onSelectTab(bg.id)

        val prompt = f.state().externalPrompt
        assertNotNull(prompt)
        assertEquals("tel:123", prompt!!.uri)
        assertTrue("queued request must be confirmed, not auto-launched", launcher.calls.isEmpty())
    }

    @Test
    fun ac25_promptOfBackgroundTab_isHiddenWhenSwitchingAway() {
        val s = f.selected()
        val firstId = f.tabManager.selectedTabId.value!!
        s.emit("tel:123", gesture = false)
        assertNotNull(f.state().externalPrompt)
        val other = f.tabManager.openTab("https://b.com", select = false)
        vm.onSelectTab(other.id)
        assertNull(f.state().externalPrompt)
        vm.onSelectTab(firstId)
        assertEquals("tel:123", f.state().externalPrompt?.uri)
        assertTrue(launcher.calls.isEmpty())
    }

    @Test
    fun ac25_requestAppearingWhileTabSelected_withGesture_isLaunchedImmediately() {
        val bg = f.tabManager.openTab("https://b.com", select = false)
        vm.onSelectTab(bg.id)
        val spy = f.tabManager.spy(bg.id)
        spy.fake.simulatePageFinished("https://b.com")
        spy.emit("tel:9", gesture = true)
        assertEquals(listOf("tel:9"), launcher.calls)
        assertNull(f.state().externalPrompt)
    }
}
