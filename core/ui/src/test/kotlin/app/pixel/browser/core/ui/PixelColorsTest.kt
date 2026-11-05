package app.pixel.browser.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 011 AC4 – tương phản WCAG của scheme tĩnh và [ColorContrast]. */
class PixelColorsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun assertContrast(
        label: String,
        background: Color,
        foreground: Color,
    ) {
        val ratio = ColorContrast.ratio(background.toArgb(), foreground.toArgb())
        assertTrue("$label contrast $ratio < 4.5", ratio >= 4.5)
    }

    private fun assertSchemeContrast(
        name: String,
        scheme: ColorScheme,
    ) {
        assertContrast("$name primary/onPrimary", scheme.primary, scheme.onPrimary)
        assertContrast("$name surface/onSurface", scheme.surface, scheme.onSurface)
        assertContrast("$name primaryContainer/onPrimaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
        assertContrast("$name surfaceContainer/onSurface", scheme.surfaceContainer, scheme.onSurface)
    }

    @Test
    fun ac4_lightScheme_meetsContrast4_5() = assertSchemeContrast("light", PixelColors.light)

    @Test
    fun ac4_darkScheme_meetsContrast4_5() = assertSchemeContrast("dark", PixelColors.dark)

    @Test
    fun ac4_lightAndDarkSurfaces_differ() = assertNotEquals(PixelColors.light.surface, PixelColors.dark.surface)

    @Test
    fun ac4_whiteOnBlack_is21() {
        assertEquals(21.0, ColorContrast.ratio(white, black), 0.01)
        assertEquals(21.0, ColorContrast.ratio(black, white), 0.01)
    }

    @Test
    fun ac4_sameColor_is1() {
        assertEquals(1.0, ColorContrast.ratio(white, white), 1e-9)
        val blue = 0xFF3A5BA0.toInt()
        assertEquals(1.0, ColorContrast.ratio(blue, blue), 1e-9)
    }

    @Test
    fun ac4_luminance_blackIs0_whiteIs1() {
        assertEquals(0.0, ColorContrast.luminance(black), 1e-9)
        assertEquals(1.0, ColorContrast.luminance(white), 1e-9)
    }
}
