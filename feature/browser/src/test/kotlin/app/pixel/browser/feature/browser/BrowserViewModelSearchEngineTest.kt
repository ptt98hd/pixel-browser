package app.pixel.browser.feature.browser

import app.pixel.browser.core.omnibox.SearchEngine
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.feature.browser.intent.IncomingIntent
import app.pixel.browser.feature.browser.intent.IncomingIntentResolver
import app.pixel.browser.feature.browser.intent.IncomingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 014 AC10 – ViewModel dùng engine động (JVM thuần). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelSearchEngineTest {
    private val engines = MutableStateFlow(SearchEngines.Google)
    private lateinit var f: BrowserFixture
    private lateinit var vm: BrowserViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        f = BrowserFixture()
        vm = BrowserViewModel(f.tabManager, searchEngines = engines)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun submit(text: String): String {
        vm.onStartEditing()
        f.selected().resetCalls()
        vm.onSubmit(text)
        return f.selected().loadUrlCalls.last()
    }

    @Test
    fun ac10_submit_usesCurrentEngine_googleThenDuckDuckGo() {
        assertEquals("https://www.google.com/search?q=kotlin", submit("kotlin"))
        engines.value = SearchEngines.DuckDuckGo
        assertEquals("https://duckduckgo.com/?q=kotlin", submit("kotlin"))
    }

    @Test
    fun ac10_searchEngineProperty_reflectsChangeImmediately() {
        assertEquals(SearchEngines.Google, vm.searchEngine)
        engines.value = SearchEngines.Brave
        assertEquals(SearchEngines.Brave, vm.searchEngine)
    }

    @Test
    fun ac10_customEngine_percentEncodesQuery() {
        engines.value = SearchEngine("custom", "Kagi", "https://kagi.com/search?q=%s")
        assertEquals("https://kagi.com/search?q=a%20b%26c", submit("a b&c"))
    }

    @Test
    fun ac10_suggestions_searchKind_useCurrentEngine() {
        vm.onStartEditing()
        engines.value = SearchEngines.DuckDuckGo
        vm.onQueryChange("hello world")
        val s = vm.uiState.value.suggestions
        assertEquals(
            listOf(SuggestionKind.Search to "https://duckduckgo.com/?q=hello%20world"),
            s.map { it.kind to it.url },
        )
    }

    @Test
    fun ac10_suggestions_goToPlusSearch_useCurrentEngine() {
        vm.onStartEditing()
        engines.value = SearchEngines.Bing
        vm.onQueryChange("example.com")
        val s = vm.uiState.value.suggestions
        assertEquals(
            listOf(
                SuggestionKind.GoTo to "https://example.com",
                SuggestionKind.Search to "https://www.bing.com/search?q=example.com",
            ),
            s.map { it.kind to it.url },
        )
    }

    @Test
    fun ac10_incomingSearchIntent_resolvedWithCurrentEngine() {
        engines.value = SearchEngines.DuckDuckGo
        val intent =
            IncomingIntent(
                action = "android.intent.action.WEB_SEARCH",
                dataString = null,
                type = null,
                extraText = null,
                query = "kotlin",
                processText = null,
                categories = emptySet(),
                launchedFromHistory = false,
            )
        val request = IncomingIntentResolver.resolve(intent, vm.searchEngine)
        assertEquals(IncomingRequest.Search("kotlin", "https://duckduckgo.com/?q=kotlin"), request)
    }

    @Test
    fun ac10_changingEngine_doesNotReloadOrChangeOpenTabs() {
        val tab = f.tabManager.openTab("https://a.com/page")
        f.tabManager
            .spy(tab.id)
            .fake
            .simulatePageFinished("https://a.com/page")
        f.tabManager.spy(tab.id).resetCalls()
        val urlBefore = vm.uiState.value.url
        val tabsBefore = f.tabManager.tabs.value

        engines.value = SearchEngines.DuckDuckGo

        assertTrue(
            f.tabManager
                .spy(tab.id)
                .loadUrlCalls
                .isEmpty(),
        )
        assertEquals(urlBefore, vm.uiState.value.url)
        assertEquals(tabsBefore, f.tabManager.tabs.value)
    }

    @Test
    fun ac10_defaultEnginesParameter_isGoogle() {
        val plain = BrowserViewModel(f.tabManager)
        assertEquals(SearchEngines.Google, plain.searchEngine)
    }
}
