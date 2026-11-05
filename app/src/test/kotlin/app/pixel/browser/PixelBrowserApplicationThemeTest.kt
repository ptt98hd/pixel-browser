package app.pixel.browser

import app.pixel.browser.core.data.FakeThemeSettingsStore
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.data.ThemeSettingsStore
import app.pixel.browser.core.ui.NightModeController
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

/** Spec 011 (review) – [PixelBrowserApplication.updateTheme]: cập nhật lạc quan, night mode khi đổi mode, lỗi ghi. */
@RunWith(RobolectricTestRunner::class)
class PixelBrowserApplicationThemeTest {
    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val modes = mutableListOf<ThemeMode>()

    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        app.nightModeController = NightModeController { modes += it }
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> synchronized(uncaught) { uncaught += e } }
    }

    @After
    fun tearDown() {
        scope.cancel()
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }

    /** Chờ mọi việc đã xếp hàng trên `ioScope` (1 luồng, tuần tự) chạy xong. */
    private fun drainIo() = runBlocking { app.ioScope.launch {}.join() }

    @Test
    fun updateTheme_setsValueImmediately_beforeStoreWrites() {
        val store = FakeThemeSettingsStore()
        app.bindThemeStore(store, scope)
        scheduler.advanceUntilIdle()

        app.updateTheme { it.copy(dynamicColor = false) }
        assertEquals(ThemeSettings(dynamicColor = false), app.themeSettings.value)

        app.updateTheme { it.copy(dynamicColor = !it.dynamicColor) }
        assertEquals(ThemeSettings(dynamicColor = true), app.themeSettings.value)

        drainIo()
        assertEquals(listOf(ThemeSettings(dynamicColor = false), ThemeSettings(dynamicColor = true)), store.updates)
    }

    @Test
    fun updateTheme_appliesNightMode_onlyWhenModeChanges() {
        app.bindThemeStore(FakeThemeSettingsStore(ThemeSettings(ThemeMode.Light)), scope)
        scheduler.advanceUntilIdle()

        app.updateTheme { it.copy(dynamicColor = false) }
        app.updateTheme { it.copy(mode = ThemeMode.Dark) }
        app.updateTheme { it.copy(mode = ThemeMode.Dark) }
        drainIo()

        assertEquals(listOf(ThemeMode.Dark), modes)
    }

    @Test
    fun updateTheme_writeIOException_doesNotCrash_andKeepsNewValue() {
        val attempted = CountDownLatch(1)
        val failing =
            object : ThemeSettingsStore {
                override val settings: Flow<ThemeSettings> = MutableStateFlow(ThemeSettings())

                override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
                    attempted.countDown()
                    throw IOException("disk full")
                }
            }
        app.bindThemeStore(failing, scope)
        scheduler.advanceUntilIdle()

        app.updateTheme { it.copy(mode = ThemeMode.Dark) }
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        drainIo()

        assertEquals(emptyList<Throwable>(), synchronized(uncaught) { uncaught.toList() })
        assertEquals(ThemeMode.Dark, app.themeSettings.value.mode)
    }
}
