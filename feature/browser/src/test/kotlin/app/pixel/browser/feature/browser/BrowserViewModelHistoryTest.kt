package app.pixel.browser.feature.browser

import app.pixel.browser.core.history.PageVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 012 AC7 – ViewModel nối `pageFinished` của mọi session vào `PageVisitRecorder` (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelHistoryTest {
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
    private val recorder = FakePageVisitRecorder()

    private fun onPage(maxLive: Int = 6): BrowserFixture =
        BrowserFixture.onPage(a, pageVisits = recorder, maxLiveSessions = maxLive).also {
            recorder.visits.clear()
            recorder.forgotten.clear()
        }

    @Test
    fun ac7_normalTab_pageFinished_recordsExactlyOnce_withTabIdUrlAndTitle() {
        val f = onPage()
        val fake = f.selected().fake
        fake.loadUrl(b)
        fake.simulateTitle("B title")
        fake.simulatePageFinished(b)
        assertEquals(
            listOf(PageVisit(BrowserFixture.PAGE_TAB_ID, b, "B title", isPrivate = false, isError = false)),
            recorder.visits,
        )
    }

    @Test
    fun ac7_backgroundTab_finishIsRecordedWithThatTabsId() {
        val f = onPage()
        val bg = f.tabManager.openTab(b, select = false)
        f.tabManager
            .spy(bg.id)
            .fake
            .simulatePageFinished(b)
        assertEquals(listOf(bg.id), recorder.visits.map { it.tabId })
        assertEquals(listOf(b), recorder.visits.map { it.url })
    }

    @Test
    fun ac7_privateTab_isReportedWithIsPrivateTrue() {
        val f = onPage()
        val p = f.tabManager.openTab(b, select = true, isPrivate = true)
        f.tabManager
            .spy(p.id)
            .fake
            .simulatePageFinished(b)
        val visit = recorder.visits.single()
        assertEquals(p.id, visit.tabId)
        assertTrue(visit.isPrivate)
    }

    @Test
    fun ac7_mainFrameError_isReportedWithIsErrorTrue() {
        val f = onPage()
        val fake = f.selected().fake
        fake.loadUrl(b)
        fake.simulateMainFrameError()
        fake.simulatePageFinished(b)
        assertTrue(recorder.visits.single().isError)
    }

    @Test
    fun ac7_homeTab_neverRecords() {
        val f = BrowserFixture(pageVisits = recorder)
        f.viewModel.onStartEditing()
        f.viewModel.onCancelEditing()
        assertTrue(f.state().isHome)
        assertTrue(recorder.visits.isEmpty())
    }

    @Test
    fun ac7_closeTab_forgetsThatTab() {
        val f = onPage()
        val bg = f.tabManager.openTab(b, select = false)
        f.viewModel.onCloseTab(bg.id)
        assertEquals(listOf(bg.id), recorder.forgotten)
    }

    @Test
    fun ac7_closeAllTabs_forgetsEveryTab() {
        val f = onPage()
        val bg = f.tabManager.openTab(b, select = false)
        f.viewModel.onCloseAllTabs()
        assertEquals(setOf(BrowserFixture.PAGE_TAB_ID, bg.id), recorder.forgotten.toSet())
        assertEquals(2, recorder.forgotten.size)
    }

    @Test
    fun ac7_sessionDiscardedThenRebuilt_stillRecords() {
        val f = onPage(maxLive = 1)
        val old = f.tabManager.session(BrowserFixture.PAGE_TAB_ID)
        f.tabManager.openTab(b, select = true) // discard tab "page"
        f.viewModel.onSelectTab(BrowserFixture.PAGE_TAB_ID) // revive "page" bằng session mới
        recorder.visits.clear()
        val revived = f.tabManager.spy(BrowserFixture.PAGE_TAB_ID)
        assertNotSame(old, revived)
        revived.fake.simulatePageFinished(a)
        assertEquals(listOf(BrowserFixture.PAGE_TAB_ID), recorder.visits.map { it.tabId })
    }

    @Test
    fun ac7_recomputeAgain_doesNotRecordTwiceForOneFinish() {
        val f = onPage()
        val fake = f.selected().fake
        fake.loadUrl(b)
        fake.simulatePageFinished(b)
        f.viewModel.onStartEditing()
        f.viewModel.onCancelEditing()
        f.tabManager.openTab("https://c.com/", select = false) // dựng lại việc theo dõi session
        f.viewModel.onReload()
        assertEquals(1, recorder.visits.size)
    }
}
