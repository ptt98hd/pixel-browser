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
}
