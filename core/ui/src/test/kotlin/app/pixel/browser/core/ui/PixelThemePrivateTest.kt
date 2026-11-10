package app.pixel.browser.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 016 AC21 – bảng màu tối cố định cho tab ẩn danh. */
@RunWith(RobolectricTestRunner::class)
class PixelThemePrivateTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(
        settings: ThemeSettings,
        private: Boolean,
    ): ColorScheme {
        var scheme: ColorScheme? = null
        rule.setContent { PixelTheme(settings, private = private) { scheme = MaterialTheme.colorScheme } }
        rule.waitForIdle()
        return checkNotNull(scheme)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    @Test
    fun ac21_private_ignoresLightModeAndDynamicColor() {
        val scheme = capture(ThemeSettings(mode = ThemeMode.Light, dynamicColor = true), private = true)
        assertSameColors("private + Light + dynamic", PrivateColorScheme, scheme)
    }

    @Test
    fun ac21_private_ignoresSystemModeAndStaticColor() {
        val scheme = capture(ThemeSettings(mode = ThemeMode.System, dynamicColor = false), private = true)
        assertSameColors("private + System + static", PrivateColorScheme, scheme)
    }

    @Test
    fun ac21_private_ignoresDarkModeAndDynamicColor() {
        val scheme = capture(ThemeSettings(mode = ThemeMode.Dark, dynamicColor = true), private = true)
        assertSameColors("private + Dark + dynamic", PrivateColorScheme, scheme)
    }

    @Test
    fun ac21_notPrivate_keepsRegularPalette() {
        val scheme = capture(ThemeSettings(mode = ThemeMode.Light, dynamicColor = false), private = false)
        assertSameColors("regular light", PixelColors.light, scheme)
    }

    @Test
    fun ac21_privateScheme_isDark() {
        assertTrue(PrivateColorScheme.surface.luminance() < DARK_SURFACE_MAX_LUMINANCE)
        assertTrue(PrivateColorScheme.background.luminance() < DARK_SURFACE_MAX_LUMINANCE)
    }

    @Test
    fun ac21_privateScheme_isDistinctFromRegularDark() {
        assertNotEquals(PixelColors.dark.colorValues(), PrivateColorScheme.colorValues())
    }

    @Test
    fun ac21_privateScheme_textOnSurface_meetsWcagAA() {
        assertTrue(contrast(PrivateColorScheme.onSurface, PrivateColorScheme.surface) >= WCAG_AA)
    }

    @Test
    fun ac21_privateScheme_onPrimaryOnPrimary_meetsWcagAA() {
        assertTrue(contrast(PrivateColorScheme.onPrimary, PrivateColorScheme.primary) >= WCAG_AA)
    }

    private companion object {
        const val WCAG_AA = 4.5
        const val DARK_SURFACE_MAX_LUMINANCE = 0.1f
    }
}
