package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineResponse
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 007 – AC21 (popup → tab mới) và AC22 (`window.close()`), JVM. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelPopupTest {
    private lateinit var f: BrowserFixture
    private lateinit var a: String

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture()
        a = f.tabManager.selectedTabId.value!!
        f.selected().visit("https://a.com/")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ids() =
        f.tabManager.tabs.value
            .map { it.id }

    /** Tab đang chọn mở popup có gesture; trả id tab popup. */
    private fun openPopupFrom(opener: String): String {
        f.viewModel.onSelectTab(opener)
        assertTrue(
            f.tabManager
                .spy(opener)
                .fake
                .simulateCreateWindow(isUserGesture = true),
        )
        val popup = f.tabManager.selectedTabId.value!!
        assertTrue("popup phải là tab mới", popup != opener)
        return popup
    }

    @Test
    fun ac21_newWindow_opensTabRightAfterOpener_selectsIt_andAttachesItsSession() {
        val x = f.tabManager.openTab("https://x.com/", select = false).id
        val countBefore = f.state().tabCount
        val opener = f.tabManager.spy(a)

        val p = openPopupFrom(a)

        assertEquals(listOf(a, p, x), ids())
        assertEquals(p, f.state().selectedTabId)
        assertEquals(countBefore + 1, f.state().tabCount)
        val response = opener.fake.responses[1L]
        assertTrue("response: $response", response is EngineResponse.AttachWindow)
        assertSame(f.tabManager.session(p), (response as EngineResponse.AttachWindow).target)
        assertTrue(
            opener.fake.requests.value
                .isEmpty(),
        )
    }

    @Test
    fun ac21_popupTab_isNotNavigatedByViewModel() {
        val p = openPopupFrom(a)
        assertTrue(
            f.tabManager
                .spy(p)
                .loadUrlCalls
                .isEmpty(),
        )
    }

    @Test
    fun ac21_popupBlocked_dismisses_showsMessage_andKeepsTabs() {
        val before = ids()
        assertNull(f.state().message)
        assertFalse(f.selected().fake.simulateCreateWindow(isUserGesture = false))
        assertEquals(EngineResponse.Dismiss(), f.selected().fake.responses[1L])
        assertNotNull(f.state().message)
        assertEquals(before, ids())
        assertNull(f.state().pendingRequest)
    }

    @Test
    fun ac22_closeWindow_fromPopup_closesIt_andReselectsOpener() {
        f.viewModel.onNewTab()
        val x = f.tabManager.selectedTabId.value!!
        val p = openPopupFrom(a)
        assertEquals(listOf(a, p, x), ids())

        f.tabManager
            .spy(p)
            .fake
            .simulateCloseWindow()

        assertEquals(listOf(a, x), ids())
        assertEquals(a, f.tabManager.selectedTabId.value)
    }

    @Test
    fun ac22_closeWindow_afterOpenerClosed_followsDefaultSelectionRule() {
        f.viewModel.onNewTab()
        val x = f.tabManager.selectedTabId.value!!
        val p = openPopupFrom(a)
        f.viewModel.onCloseTab(a)
        f.viewModel.onSelectTab(p)

        f.tabManager
            .spy(p)
            .fake
            .simulateCloseWindow()

        assertEquals(listOf(x), ids())
        assertEquals(x, f.tabManager.selectedTabId.value)
    }

    @Test
    fun ac22_closeWindow_fromNormalTab_onlyDismisses() {
        val before = ids()
        val fake = f.selected().fake
        fake.simulateCloseWindow()
        assertEquals(before, ids())
        assertEquals(EngineResponse.Dismiss(), fake.responses[1L])
        assertFalse(fake.isClosed)
    }

    @Test
    fun ac22_closeWindow_fromBackgroundPopup_closesIt_withoutChangingSelection() {
        val p = openPopupFrom(a)
        f.viewModel.onSelectTab(a)
        f.tabManager
            .spy(p)
            .fake
            .simulateCloseWindow()
        assertFalse(p in ids())
        assertEquals(a, f.tabManager.selectedTabId.value)
    }
}
