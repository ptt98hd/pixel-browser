package app.pixel.browser.core.ui

import app.pixel.browser.core.data.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 011 AC1. */
class ThemeResolutionTest {
    @Test
    fun ac1_system_followsSystemLight() = assertFalse(resolveDarkTheme(ThemeMode.System, systemDark = false))

    @Test
    fun ac1_system_followsSystemDark() = assertTrue(resolveDarkTheme(ThemeMode.System, systemDark = true))

    @Test
    fun ac1_light_ignoresDarkSystem() = assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark = true))

    @Test
    fun ac1_dark_ignoresLightSystem() = assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark = false))

    @Test
    fun ac1_light_and_dark_areFixedRegardlessOfSystem() {
        listOf(false, true).forEach { systemDark ->
            assertFalse(resolveDarkTheme(ThemeMode.Light, systemDark))
            assertTrue(resolveDarkTheme(ThemeMode.Dark, systemDark))
        }
    }
}
