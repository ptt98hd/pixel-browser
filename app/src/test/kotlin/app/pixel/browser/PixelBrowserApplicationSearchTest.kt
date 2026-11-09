package app.pixel.browser

import app.pixel.browser.core.data.search.CustomSearchEngine
import app.pixel.browser.core.data.search.FakeSearchSettingsStore
import app.pixel.browser.core.data.search.SearchSettings
import app.pixel.browser.core.data.search.SearchSettingsStore
import app.pixel.browser.core.omnibox.SearchEngines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Spec 014 AC17 – [PixelBrowserApplication.searchSettings]/`updateSearch`/`bindSearchStore` (cùng mẫu theme). */
@RunWith(RobolectricTestRunner::class)
class PixelBrowserApplicationSearchTest {
    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))

    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } }
    }

    @After
    fun tearDown() {
        scope.cancel()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    private fun drainIo() = runBlocking { app.ioScope.launch {}.join() }

    @Test
    fun ac17_beforeLoad_settingsAreDefaults() {
        app.bindSearchStore(FakeSearchSettingsStore(SearchSettings(selectedId = "duckduckgo")), scope)
        assertEquals(SearchSettings(), app.searchSettings.value)
        assertEquals(SearchEngines.Google, app.searchEngine.value)
    }

    @Test
    fun ac17_afterLoad_engineIsFromStore() {
        app.bindSearchStore(FakeSearchSettingsStore(SearchSettings(selectedId = "duckduckgo")), scope)
        scheduler.advanceUntilIdle()
        assertEquals(SearchSettings(selectedId = "duckduckgo"), app.searchSettings.value)
        assertEquals(SearchEngines.DuckDuckGo, app.searchEngine.value)
    }

    @Test
    fun ac17_updateSearch_setsValueImmediately_andWritesStoreOnce() {
        val store = FakeSearchSettingsStore()
        app.bindSearchStore(store, scope)
        scheduler.advanceUntilIdle()

        app.updateSearch { it.copy(selectedId = "bing") }
        assertEquals(SearchSettings(selectedId = "bing"), app.searchSettings.value)
        assertEquals(SearchEngines.Bing, app.searchEngine.value)

        drainIo()
        assertEquals(listOf(SearchSettings(selectedId = "bing")), store.updates)
    }

    @Test
    fun ac17_updateSearch_toSameValue_doesNotWrite() {
        val store = FakeSearchSettingsStore()
        app.bindSearchStore(store, scope)
        scheduler.advanceUntilIdle()
        app.updateSearch { it }
        drainIo()
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun ac17_readIOException_keepsDefaults_withoutCrash() {
        app.bindSearchStore(FakeSearchSettingsStore(readFailure = IOException("read")), scope)
        scheduler.advanceUntilIdle()
        assertEquals(SearchSettings(), app.searchSettings.value)
        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
    }

    @Test
    fun ac17_writeIOException_isOnlyLogged_valueStaysInMemory() {
        val attempted = CountDownLatch(1)
        val failing =
            object : SearchSettingsStore {
                override val settings: Flow<SearchSettings> = MutableStateFlow(SearchSettings())

                override suspend fun update(transform: (SearchSettings) -> SearchSettings) {
                    attempted.countDown()
                    throw IOException("disk full")
                }
            }
        app.bindSearchStore(failing, scope)
        scheduler.advanceUntilIdle()

        app.updateSearch { it.copy(selectedId = "brave") }
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        drainIo()

        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals("brave", app.searchSettings.value.selectedId)
    }

    @Test
    fun ac17_valueEmittedByStoreWhileWritePending_doesNotOverrideOptimisticValue() {
        val store = FakeSearchSettingsStore()
        app.bindSearchStore(store, scope)
        scheduler.advanceUntilIdle()
        app.updateSearch { it.copy(selectedId = "brave") }
        app.updateSearch { it.copy(selectedId = "bing") }
        scheduler.advanceUntilIdle()
        drainIo()
        scheduler.advanceUntilIdle()
        assertEquals("bing", app.searchSettings.value.selectedId)
    }

    @Test
    fun ac17_updateBeforeFirstLoad_isAppliedToLoadedValue_notDefaults() {
        val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")
        val store = FakeSearchSettingsStore(SearchSettings("google", kagi))
        app.bindSearchStore(store, scope)

        app.updateSearch { it.copy(selectedId = "bing") }
        scheduler.advanceUntilIdle()
        drainIo()
        scheduler.advanceUntilIdle()

        assertEquals(SearchSettings("bing", kagi), app.searchSettings.value)
        assertEquals(listOf(SearchSettings("bing", kagi)), store.updates)
    }

    @Test
    fun ac17_updateBeforeLoad_whenReadFails_appliesToDefaults() {
        val store = FakeSearchSettingsStore(readFailure = IOException("read"))
        app.bindSearchStore(store, scope)
        app.updateSearch { it.copy(selectedId = "brave") }
        scheduler.advanceUntilIdle()
        drainIo()
        assertEquals("brave", app.searchSettings.value.selectedId)
        assertEquals(listOf(SearchSettings(selectedId = "brave")), store.updates)
    }
}
