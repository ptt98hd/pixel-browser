package app.pixel.browser.feature.browser

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

/** Spec 015 AC4–AC7 – ViewModel tìm trong trang (JVM, `FakeEngineSession`). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelFindTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val page = "https://a.com/"

    private fun onPage(url: String = page) = BrowserFixture.onPage(url)

    private val BrowserFixture.fake get() = selected().fake

    private fun BrowserFixture.openFind() = viewModel.onOpenFind()

    // --- AC4 ---

    @Test
    fun ac4_webTab_canFind_barClosedByDefault() {
        val f = onPage()
        assertTrue(f.state().canFind)
        assertNull(f.state().find)
    }

    @Test
    fun ac4_openFind_showsEmptyBar_withoutSearching() {
        val f = onPage()
        f.openFind()
        assertEquals(FindUiState("", 0, 0, false), f.state().find)
        assertTrue(f.fake.findQueries.isEmpty())
    }

    @Test
    fun ac4_openFind_whileEditingUrl_cancelsEditing() {
        val f = onPage()
        f.viewModel.onStartEditing()
        assertTrue(f.state().isEditingUrl)
        f.openFind()
        assertFalse(f.state().isEditingUrl)
        assertNotNull(f.state().find)
    }

    @Test
    fun ac4_atHome_cannotFind_andOpenDoesNothing() {
        val f = BrowserFixture()
        assertTrue(f.state().isHome)
        assertFalse(f.state().canFind)
        f.openFind()
        assertNull(f.state().find)
    }

    @Test
    fun ac4_aboutBlank_cannotFind_andOpenDoesNothing() {
        val f = onPage("about:blank")
        assertFalse(f.state().canFind)
        f.openFind()
        assertNull(f.state().find)
    }

    @Test
    fun ac4_closeFind_closesBar_andClearsOnce_evenWithoutTyping() {
        val f = onPage()
        f.openFind()
        f.viewModel.onCloseFind()
        assertNull(f.state().find)
        assertEquals(1, f.fake.clearFindCalls)
    }

    @Test
    fun ac4_startEditing_whileBarOpen_closesBar_andClears() {
        val f = onPage()
        f.openFind()
        f.viewModel.onStartEditing()
        assertNull(f.state().find)
        assertTrue(f.state().isEditingUrl)
        assertEquals(1, f.fake.clearFindCalls)
    }

    @Test
    fun ac4_reopen_startsWithEmptyField() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("kot")
        f.viewModel.onCloseFind()
        f.openFind()
        assertEquals(FindUiState("", 0, 0, false), f.state().find)
    }

    @Test
    fun ac4_fullscreen_cannotFind_andClosesOpenBar() {
        val f = onPage()
        f.openFind()
        f.fake.simulateEnterFullscreen()
        assertFalse(f.state().canFind)
        assertNull(f.state().find)
    }

    // --- AC5 ---

    @Test
    fun ac5_eachChange_callsFindOnce_noDebounce() {
        val f = onPage()
        f.openFind()
        listOf("k", "ko", "kot").forEach(f.viewModel::onFindQueryChange)
        assertEquals(listOf("k", "ko", "kot"), f.fake.findQueries)
    }

    @Test
    fun ac5_blankQuery_clearsInsteadOfFinding() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("k")
        f.viewModel.onFindQueryChange("")
        f.viewModel.onFindQueryChange("   ")
        assertEquals(listOf("k"), f.fake.findQueries)
        assertEquals(2, f.fake.clearFindCalls)
    }

    @Test
    fun ac5_engineResult_isMappedToOneBasedCurrent() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("kot")
        f.fake.simulateFindResult(active = 2, total = 7, done = true)
        assertEquals(FindUiState("kot", current = 3, total = 7, isSearching = false), f.state().find)
    }

    @Test
    fun ac5_noMatches_whenDone_hasCurrentZero() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("zzz")
        f.fake.simulateFindResult(active = 0, total = 0, done = true)
        assertEquals(FindUiState("zzz", 0, 0, false), f.state().find)
    }

    @Test
    fun ac5_whileCounting_isSearching() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("kot")
        assertEquals(FindUiState("kot", 0, 0, true), f.state().find)
        f.fake.simulateFindResult(active = 0, total = 3, done = false)
        assertTrue(f.state().find!!.isSearching)
        assertEquals(3, f.state().find!!.total)
    }

    @Test
    fun ac5_uiQuery_isWhatTheUserTyped_notTruncated() {
        val f = onPage()
        f.openFind()
        val long = "x".repeat(300)
        f.viewModel.onFindQueryChange(long)
        assertEquals(
            300,
            f
                .state()
                .find!!
                .query.length,
        )
        assertEquals(
            256,
            f.fake.findQueries
                .last()
                .length,
        )
    }

    @Test
    fun ac5_closedBar_ignoresQueryChange() {
        val f = onPage()
        f.viewModel.onFindQueryChange("kot")
        assertTrue(f.fake.findQueries.isEmpty())
        assertNull(f.state().find)
    }

    // --- AC6 ---

    @Test
    fun ac6_next_forwardsDirection() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("kot")
        f.fake.simulateFindResult(0, 4)
        f.viewModel.onFindNext(true)
        f.viewModel.onFindNext(false)
        assertEquals(listOf(true, false), f.fake.findNextCalls)
    }

    @Test
    fun ac6_next_withNoResults_doesNothing() {
        val f = onPage()
        f.openFind()
        f.viewModel.onFindQueryChange("zzz")
        f.fake.simulateFindResult(0, 0)
        f.viewModel.onFindNext(true)
        assertTrue(f.fake.findNextCalls.isEmpty())
    }

    @Test
    fun ac6_next_withClosedBar_doesNothing() {
        val f = onPage()
        f.viewModel.onFindNext(true)
        assertTrue(f.fake.findNextCalls.isEmpty())
    }

    // --- AC7 ---

    private fun BrowserFixture.withBarAndOtherTab(): Pair<BrowserFixture, String> {
        val other = tabManager.openTab("https://b.com/", select = false).id
        openFind()
        viewModel.onFindQueryChange("kot")
        assertNotNull(state().find)
        return this to other
    }

    private fun assertClosedAndStaysClosedOnReturn(
        f: BrowserFixture,
        original: String,
    ) {
        assertNull(f.state().find)
        f.viewModel.onSelectTab(original)
        assertNull(f.state().find)
    }

    @Test
    fun ac7_selectOtherTab_closesBar_andClearsOldSession() {
        val (f, other) = onPage().withBarAndOtherTab()
        val original = f.tabManager.selectedTabId.value!!
        val oldFake = f.fake
        f.viewModel.onSelectTab(other)
        assertEquals(1, oldFake.clearFindCalls)
        assertClosedAndStaysClosedOnReturn(f, original)
    }

    @Test
    fun ac7_newTab_closesBar() {
        val (f, _) = onPage().withBarAndOtherTab()
        val original = f.tabManager.selectedTabId.value!!
        val oldFake = f.fake
        f.viewModel.onNewTab()
        assertEquals(1, oldFake.clearFindCalls)
        assertClosedAndStaysClosedOnReturn(f, original)
    }

    @Test
    fun ac7_home_closesBar() {
        val (f, _) = onPage().withBarAndOtherTab()
        val original = f.tabManager.selectedTabId.value!!
        val oldFake = f.fake
        f.viewModel.onHome()
        assertEquals(1, oldFake.clearFindCalls)
        assertClosedAndStaysClosedOnReturn(f, original)
    }

    @Test
    fun ac7_closeSelectedTab_closesBar() {
        val (f, other) = onPage().withBarAndOtherTab()
        f.viewModel.onCloseTab(f.tabManager.selectedTabId.value!!)
        assertNull(f.state().find)
        f.viewModel.onSelectTab(other)
        assertNull(f.state().find)
    }

    @Test
    fun ac7_openTabSwitcher_closesBar() {
        val (f, _) = onPage().withBarAndOtherTab()
        val original = f.tabManager.selectedTabId.value!!
        val oldFake = f.fake
        f.viewModel.onOpenTabSwitcher()
        assertEquals(1, oldFake.clearFindCalls)
        assertClosedAndStaysClosedOnReturn(f, original)
    }

    @Test
    fun ac7_mainFrameUrlChange_closesBar() {
        val (f, _) = onPage().withBarAndOtherTab()
        val original = f.tabManager.selectedTabId.value!!
        f.fake.simulatePageStarted("https://b.com/")
        assertEquals(1, f.fake.clearFindCalls)
        assertClosedAndStaysClosedOnReturn(f, original)
    }

    @Test
    fun ac7_backgroundTabNavigating_doesNotAffectBar() {
        val (f, other) = onPage().withBarAndOtherTab()
        val background = f.tabManager.spy(other).fake
        background.simulatePageStarted("https://c.com/")
        assertNotNull(f.state().find)
        assertEquals(0, f.fake.clearFindCalls)
    }

    @Test
    fun ac7_selectingTheSelectedTab_keepsBarOpen() {
        val (f, _) = onPage().withBarAndOtherTab()
        f.viewModel.onSelectTab(f.tabManager.selectedTabId.value!!)
        assertNotNull(f.state().find)
        assertEquals(0, f.fake.clearFindCalls)
    }
}
