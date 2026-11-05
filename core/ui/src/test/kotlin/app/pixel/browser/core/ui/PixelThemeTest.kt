package app.pixel.browser.core.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Spec 011 AC6 – [PixelTheme] chọn color scheme/shapes/typography theo [ThemeSettings]. */
@RunWith(RobolectricTestRunner::class)
class PixelThemeTest {
    @get:Rule
    val rule = createComposeRule()

    private class Captured(
        val scheme: ColorScheme,
        val shapes: Shapes,
        val typography: Typography,
        val context: Context,
    )

    private fun capture(settings: ThemeSettings): Captured {
        var captured: Captured? = null
        rule.setContent {
            PixelTheme(settings) {
                captured =
                    Captured(
                        MaterialTheme.colorScheme,
                        MaterialTheme.shapes,
                        MaterialTheme.typography,
                        LocalContext.current,
                    )
            }
        }
        rule.waitForIdle()
        return checkNotNull(captured) { "PixelTheme không gọi content" }
    }

    /** Điều kiện tiên quyết: Robolectric trả dynamic light ≠ dark, nếu không test so sánh vô nghĩa. */
    private fun assumeDynamicSchemesDiffer(context: Context) {
        assumeTrue(dynamicLightColorScheme(context).colorValues() != dynamicDarkColorScheme(context).colorValues())
    }

    @Test
    fun ac6_light_dynamic_usesDynamicLightScheme() {
        val c = capture(ThemeSettings(mode = ThemeMode.Light, dynamicColor = true))
        assumeDynamicSchemesDiffer(c.context)
        assertSameColors("Light + dynamic", dynamicLightColorScheme(c.context), c.scheme)
    }

    @Test
    fun ac6_light_static_usesPixelColorsLight() {
        val c = capture(ThemeSettings(mode = ThemeMode.Light, dynamicColor = false))
        assertSameColors("Light + static", PixelColors.light, c.scheme)
    }

    @Test
    fun ac6_dark_dynamic_usesDynamicDarkScheme() {
        val c = capture(ThemeSettings(mode = ThemeMode.Dark, dynamicColor = true))
        assumeDynamicSchemesDiffer(c.context)
        assertSameColors("Dark + dynamic", dynamicDarkColorScheme(c.context), c.scheme)
    }

    @Test
    fun ac6_dark_static_usesPixelColorsDark() {
        val c = capture(ThemeSettings(mode = ThemeMode.Dark, dynamicColor = false))
        assertSameColors("Dark + static", PixelColors.dark, c.scheme)
    }

    @Test
    @Config(qualifiers = "night")
    fun ac6_system_inNightConfig_usesDarkSchemes() {
        val dynamic = capture(ThemeSettings(mode = ThemeMode.System, dynamicColor = true))
        assumeDynamicSchemesDiffer(dynamic.context)
        assertSameColors("System(night) + dynamic", dynamicDarkColorScheme(dynamic.context), dynamic.scheme)
    }

    @Test
    @Config(qualifiers = "night")
    fun ac6_system_inNightConfig_static_usesPixelColorsDark() {
        val c = capture(ThemeSettings(mode = ThemeMode.System, dynamicColor = false))
        assertSameColors("System(night) + static", PixelColors.dark, c.scheme)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun ac6_system_inNotNightConfig_usesLightSchemes() {
        val c = capture(ThemeSettings(mode = ThemeMode.System, dynamicColor = true))
        assumeDynamicSchemesDiffer(c.context)
        assertSameColors("System(notnight) + dynamic", dynamicLightColorScheme(c.context), c.scheme)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun ac6_system_inNotNightConfig_static_usesPixelColorsLight() {
        val c = capture(ThemeSettings(mode = ThemeMode.System, dynamicColor = false))
        assertSameColors("System(notnight) + static", PixelColors.light, c.scheme)
    }

    @Test
    @Config(qualifiers = "night")
    fun ac6_explicitLight_overridesNightConfig() {
        val c = capture(ThemeSettings(mode = ThemeMode.Light, dynamicColor = false))
        assertSameColors("Light trong night", PixelColors.light, c.scheme)
    }

    @Test
    fun ac6_shapesAndTypography_arePixelTokens() {
        val c = capture(ThemeSettings())
        assertEquals(PixelShapes, c.shapes)
        assertEquals(PixelTypography, c.typography)
    }

    @Test
    fun ac6_pixelColorScheme_matchesDynamicOrStaticSelection() {
        val context = RuntimeEnvironment.getApplication()
        assertSameColors(
            "static light",
            PixelColors.light,
            pixelColorScheme(context, dark = false, dynamicColor = false),
        )
        assertSameColors("static dark", PixelColors.dark, pixelColorScheme(context, dark = true, dynamicColor = false))
        assertSameColors(
            "dynamic light",
            dynamicLightColorScheme(context),
            pixelColorScheme(context, dark = false, dynamicColor = true),
        )
        assertSameColors(
            "dynamic dark",
            dynamicDarkColorScheme(context),
            pixelColorScheme(context, dark = true, dynamicColor = true),
        )
        assertNotEquals(
            PixelColors.light.colorValues(),
            pixelColorScheme(context, dark = true, dynamicColor = false).colorValues(),
        )
    }
}
