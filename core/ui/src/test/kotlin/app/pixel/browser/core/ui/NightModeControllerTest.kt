package app.pixel.browser.core.ui

import android.app.UiModeManager
import app.pixel.browser.core.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 011 AC12 (phần controller). */
@RunWith(RobolectricTestRunner::class)
class NightModeControllerTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun applied(mode: ThemeMode): List<Int> {
        val calls = mutableListOf<Int>()
        UiModeNightModeController(context) { calls += it }.apply(mode)
        return calls
    }

    @Test
    fun ac12_dark_setsModeNightYes() = assertEquals(listOf(UiModeManager.MODE_NIGHT_YES), applied(ThemeMode.Dark))

    @Test
    fun ac12_light_setsModeNightNo() = assertEquals(listOf(UiModeManager.MODE_NIGHT_NO), applied(ThemeMode.Light))

    @Test
    fun ac12_system_setsModeNightAuto() = assertEquals(listOf(UiModeManager.MODE_NIGHT_AUTO), applied(ThemeMode.System))

    @Test
    fun ac12_defaultConstructor_appliesWithoutThrowingUnderRobolectric() {
        val controller = UiModeNightModeController(context)
        ThemeMode.entries.forEach { controller.apply(it) }
    }
}
