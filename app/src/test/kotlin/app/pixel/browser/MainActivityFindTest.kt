package app.pixel.browser

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 015 AC11 – tìm trong trang end-to-end với `FakeEngineSession` (nối qua `bindBaseSessionFactory`). */
@RunWith(RobolectricTestRunner::class)
class MainActivityFindTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication
    private var scenario: ActivityScenario<MainActivity>? = null
    private val page = "https://example.org/page"

    @Before
    fun bind() {
        app.bindHistoryStore(InMemoryHistoryStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindBookmarkRepository(InMemoryBookmarkRepository())
        app.bindBaseSessionFactory { FakeEngineSession() }
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

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun onMain(
        s: ActivityScenario<MainActivity>,
        block: () -> Unit,
    ) {
        s.onActivity { block() }
        rule.waitForIdle()
    }

    @Test
    fun ac11_openFind_type_next_close_endToEnd() {
        val s = launch()
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl(page) }
        val fake = vm.selectedSession.value as FakeEngineSession
        onMain(s) { fake.simulatePageFinished(page) }

        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_FIND)
        rule.onNodeWithTag(BrowserTestTags.FIND_BAR).assertExists()

        listOf("k", "o", "t").forEach { rule.onNodeWithTag(BrowserTestTags.FIND_FIELD).performTextInput(it) }
        rule.waitForIdle()
        assertEquals(listOf("k", "ko", "kot"), fake.findQueries)

        onMain(s) { fake.simulateFindResult(0, 4) }
        rule.onNodeWithTag(BrowserTestTags.FIND_COUNT).assertTextEquals("1/4")

        tap(BrowserTestTags.FIND_NEXT)
        assertEquals(listOf(true), fake.findNextCalls)
        onMain(s) { fake.simulateFindResult(1, 4) }
        rule.onNodeWithTag(BrowserTestTags.FIND_COUNT).assertTextEquals("2/4")

        tap(BrowserTestTags.FIND_CLOSE)
        rule.onNodeWithTag(BrowserTestTags.FIND_BAR).assertDoesNotExist()
        assertEquals(1, fake.clearFindCalls)
    }

    @Test
    fun ac11_systemBack_closesBar_beforeGoingBack() {
        val s = launch()
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl(page) }
        val fake = vm.selectedSession.value as FakeEngineSession
        onMain(s) { fake.simulatePageFinished(page) }
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_FIND)
        s.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.FIND_BAR).assertDoesNotExist()
        assertEquals(1, fake.clearFindCalls)
    }
}
