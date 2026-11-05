package app.pixel.browser.feature.browser

import app.pixel.browser.feature.browser.intent.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 007 AC20 (phần ViewModel, JVM): trạng thái fullscreen và thoát fullscreen trước khi đổi ngữ cảnh. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelFullscreenTest {
    private lateinit var f: BrowserFixture
    private lateinit var a: String
    private lateinit var b: String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture()
        a = f.tabManager.selectedTabId.value!!
        f.selected().visit("https://a.com/")
        b = f.tabManager.openTab("https://b.com/", select = false).id
        f.selected().fake.simulateEnterFullscreen()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val fakeA get() = f.tabManager.spy(a).fake

    @Test
    fun ac20_uiState_reflectsSelectedTabFullscreen() {
        assertTrue(f.state().isFullscreen)
        f.viewModel.onExitFullscreen()
        assertEquals(1, fakeA.exitFullscreenCalls)
        assertFalse(f.state().isFullscreen)
    }

    @Test
    fun ac20_backgroundTabFullscreen_isNotReported() {
        f.viewModel.onExitFullscreen()
        f.tabManager
            .spy(b)
            .fake
            .simulateEnterFullscreen()
        assertFalse(f.state().isFullscreen)
    }

    @Test
    fun ac20_selectOtherTab_exitsFullscreenFirst() {
        f.viewModel.onSelectTab(b)
        assertEquals(1, fakeA.exitFullscreenCalls)
        assertFalse(f.state().isFullscreen)
    }

    @Test
    fun ac20_closeFullscreenTab_exitsFullscreenFirst() {
        val fake = fakeA
        f.viewModel.onCloseTab(a)
        assertEquals(1, fake.exitFullscreenCalls)
    }

    @Test
    fun ac20_incomingIntent_exitsFullscreenFirst() {
        f.viewModel.onIncomingIntent(IncomingRequest.OpenUrl("https://c.com/"))
        assertEquals(1, fakeA.exitFullscreenCalls)
        assertFalse(f.state().isFullscreen)
    }

    @Test
    fun ac20_notFullscreen_selectTab_doesNotCallExit() {
        f.viewModel.onExitFullscreen()
        f.viewModel.onSelectTab(b)
        assertEquals(1, fakeA.exitFullscreenCalls)
    }
}
