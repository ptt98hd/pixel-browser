package app.pixel.browser

import android.app.Activity
import androidx.core.view.WindowCompat
import app.pixel.browser.core.data.FakeThemeSettingsStore
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Spec 011 AC14 – cài đặt theme nạp trước khi Activity vẽ. Không dùng compose rule để không có Activity nào
 * chạy trước khi nạp xong.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityThemePreloadTest {
    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    private val scheduler = TestCoroutineScheduler()
    private val errors = mutableListOf<Throwable>()
    private val scope =
        CoroutineScope(
            SupervisorJob() + StandardTestDispatcher(scheduler) + CoroutineExceptionHandler { _, e -> errors += e },
        )

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun lightStatusBars(activity: Activity): Boolean =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars

    @Test
    @Config(qualifiers = "notnight")
    fun ac14_preloadedDarkSettings_areAppliedBeforeFirstFrame() {
        app.bindThemeStore(FakeThemeSettingsStore(ThemeSettings(ThemeMode.Dark, dynamicColor = false)), scope)
        scheduler.advanceUntilIdle()
        assertEquals(ThemeSettings(ThemeMode.Dark, dynamicColor = false), app.themeSettings.value)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        // Ngay sau setup(), chưa đẩy thêm frame: icon status bar sáng (nền tối).
        assertFalse(lightStatusBars(activity))
        assertEquals(emptyList<Throwable>(), errors)
        controller.pause().stop().destroy()
    }

    @Test
    fun ac14_storeReadFailure_keepsDefaults_andActivityStartsNormally() {
        app.bindThemeStore(FakeThemeSettingsStore(readFailure = IOException("disk error")), scope)
        scheduler.advanceUntilIdle()

        assertEquals(ThemeSettings(), app.themeSettings.value)
        assertEquals("IOException phải được xử lý, không lan ra scope", emptyList<Throwable>(), errors)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertFalse(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }
}
