package app.pixel.browser.feature.browser

import android.Manifest
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.JsDialogKind
import app.pixel.browser.core.engine.SitePermission
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

/** Spec 007 – AC13, AC25 (+ FIFO, AC24 URL không hỗ trợ): ViewModel chỉ đưa request của tab đang chọn lên UI. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelRequestTest {
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
        f.tabManager
            .spy(b)
            .fake
            .simulatePageFinished("https://b.com/")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fake(id: String) = f.tabManager.spy(id).fake

    @Test
    fun ac13_backgroundTabRequest_isShownOnlyWhileThatTabIsSelected() {
        val dialog = fake(b).simulateJsDialog(JsDialogKind.Alert, "from b")
        assertNotNull(dialog)
        assertNull(f.state().pendingRequest)

        f.viewModel.onSelectTab(b)
        assertEquals(dialog, f.state().pendingRequest)

        f.viewModel.onSelectTab(a)
        assertNull(f.state().pendingRequest)
        assertEquals(listOf<EngineRequest>(dialog!!), fake(b).requests.value)
    }

    @Test
    fun ac13_selectedTabDialog_isPendingImmediately() {
        val dialog = fake(a).simulateJsDialog(JsDialogKind.Confirm, "sure?")
        assertEquals(dialog, f.state().pendingRequest)
    }

    @Test
    fun ac13_onRespond_answersSelectedTabRequest_andClearsPending() {
        val dialog = fake(a).simulateJsDialog(JsDialogKind.Confirm, "sure?")!!
        f.viewModel.onRespond(dialog.id, EngineResponse.Confirm())
        assertEquals(EngineResponse.Confirm(), fake(a).responses[dialog.id])
        assertNull(f.state().pendingRequest)
    }

    @Test
    fun ac13_requestsHandledByViewModel_neverBecomePending() {
        val seen = mutableListOf<EngineRequest?>()
        val tracker = f.viewModel.uiState

        fun record() {
            seen += tracker.value.pendingRequest
        }
        fake(a).simulateCreateWindow(isUserGesture = false)
        record()
        val popupId = f.tabManager.selectedTabId.value!!
        // Popup có gesture mở tab mới (AC21); request NewWindow không bao giờ hiện.
        f.viewModel.onSelectTab(a)
        fake(a).simulateCreateWindow(isUserGesture = true)
        record()
        val p = f.tabManager.selectedTabId.value!!
        f.tabManager
            .spy(p)
            .fake
            .simulateCloseWindow()
        record()
        f.permissionMemory.set("https://a.com", SitePermission.Camera, true)
        f.gate.alreadyGranted += Manifest.permission.CAMERA
        f.viewModel.onSelectTab(a)
        fake(a).simulatePermissionRequest(setOf(SitePermission.Camera))
        record()
        assertTrue("pending: $seen (popup tab $popupId)", seen.all { it == null })
        assertTrue(fake(a).requests.value.isEmpty())
    }

    @Test
    fun edge_multipleRequests_shownOneAtATime_inFifoOrder() {
        fake(a).simulatePermissionRequest(setOf(SitePermission.Microphone))
        val alert = fake(a).simulateJsDialog(JsDialogKind.Alert, "second")!!
        val first = f.state().pendingRequest
        assertTrue(first is EngineRequest.PermissionPrompt)
        f.viewModel.onPermissionDecision(first!!.id, allow = false)
        assertEquals(alert, f.state().pendingRequest)
    }

    @Test
    fun ac25_closeTab_withPendingDialog_dismissesIt_andClearsPendingRequest() {
        val dialog = fake(a).simulateJsDialog(JsDialogKind.Alert, "bye")!!
        assertEquals(dialog, f.state().pendingRequest)
        val closed = fake(a)
        f.viewModel.onCloseTab(a)
        assertEquals(EngineResponse.Dismiss(), closed.responses[dialog.id])
        assertEquals(b, f.tabManager.selectedTabId.value)
        assertNull(f.state().pendingRequest)
    }

    @Test
    fun ac24_nonHttpDownload_isDismissedWithMessage_withoutDialog() {
        for (url in listOf("blob:https://a.com/uuid", "data:text/plain,hi")) {
            f.viewModel.onMessageShown()
            fake(a).simulateDownload(url)
            assertNull(url, f.state().pendingRequest)
            assertTrue(url, fake(a).requests.value.isEmpty())
            assertTrue(url, fake(a).responses.values.last() == EngineResponse.Dismiss())
            assertNotNull(url, f.state().message)
        }
    }

    @Test
    fun ac24_httpDownload_isPending() {
        fake(a).simulateDownload("https://a.com/f.pdf", "application/pdf", null, 2048)
        assertTrue(f.state().pendingRequest is EngineRequest.Download)
    }
}
