package app.pixel.browser.core.engine

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeEngineSessionContractTest : EngineSessionContractTest() {
    override fun createSession(): EngineSession = FakeEngineSession()

    override fun expectedView(session: EngineSession): View? = null

    override fun EngineSession.emitProgress(progress: Int) = (this as FakeEngineSession).simulateProgress(progress)

    override fun EngineSession.emitPageFinished(url: String) = (this as FakeEngineSession).simulatePageFinished(url)

    override fun EngineSession.emitSslError() = (this as FakeEngineSession).simulateSslError()

    override fun EngineSession.emitPageStarted(url: String) = (this as FakeEngineSession).simulatePageStarted(url)

    override fun EngineSession.emitNavigation(
        url: String,
        isMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
    ): Boolean = (this as FakeEngineSession).simulateNavigation(url, isMainFrame, hasGesture, isRedirect)

    // --- Spec 005 ---

    @Test
    fun scrollCommands_startEmpty() {
        assertTrue(FakeEngineSession().scrollCommands.isEmpty())
    }

    @Test
    fun scrollCommands_recordTopAndBottomInOrder() {
        val s = FakeEngineSession()
        s.scrollToTop()
        s.scrollToBottom()
        s.scrollToTop()
        assertEquals(listOf(ScrollCommand.Top, ScrollCommand.Bottom, ScrollCommand.Top), s.scrollCommands)
    }

    @Test
    fun scrollCommands_notRecordedAfterClose() {
        val s = FakeEngineSession()
        s.scrollToTop()
        s.close()
        s.scrollToBottom()
        assertEquals(listOf(ScrollCommand.Top), s.scrollCommands)
    }

    @Test
    fun pause_resume_areRecorded_andIgnoredAfterClose() {
        val s = FakeEngineSession()
        assertFalse(s.isPaused)
        s.onPause()
        assertTrue(s.isPaused)
        s.onResume()
        assertFalse(s.isPaused)
        s.close()
        s.onPause()
        assertFalse(s.isPaused)
    }

    // --- Spec 006 ---

    @Test
    fun ac5_simulateNavigation_defaults_areMainFrameWithGestureNoRedirect() {
        val s = FakeEngineSession()
        assertTrue(s.simulateNavigation("tel:123"))
        val r = s.requests.value.single() as EngineRequest.ExternalApp
        assertTrue(r.hasUserGesture)
        assertFalse(r.isRedirect)
        assertEquals("", r.origin)
    }

    @Test
    fun responses_recordAcceptedResponses_only() {
        val s = FakeEngineSession()
        s.simulateNavigation("tel:1")
        s.simulateNavigation("tel:2")
        s.respond(1, EngineResponse.Dismiss(suppressFurther = true))
        s.respond(99, EngineResponse.Dismiss())
        assertEquals(mapOf(1L to EngineResponse.Dismiss(suppressFurther = true)), s.responses)
    }

    @Test
    fun ac2_close_recordsDismissForEveryPendingRequest() {
        val s = FakeEngineSession()
        s.simulateNavigation("tel:1")
        s.simulateNavigation("tel:2")
        s.close()
        assertEquals(mapOf(1L to EngineResponse.Dismiss(), 2L to EngineResponse.Dismiss()), s.responses)
    }
}
