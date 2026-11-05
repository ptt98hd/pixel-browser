package app.pixel.browser.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Scheme tĩnh sinh từ seed #3A5BA0 (Material Theme Builder) – dùng khi tắt dynamic color (spec 011 AC4). */
object PixelColors {
    val light: ColorScheme =
        lightColorScheme(
            primary = Color(0xFF445E91),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD8E2FF),
            onPrimaryContainer = Color(0xFF2B4678),
            inversePrimary = Color(0xFFADC6FF),
            secondary = Color(0xFF575E71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFDBE2F9),
            onSecondaryContainer = Color(0xFF3F4759),
            tertiary = Color(0xFF715573),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFBD7FC),
            onTertiaryContainer = Color(0xFF583E5B),
            background = Color(0xFFF9F9FF),
            onBackground = Color(0xFF1A1B20),
            surface = Color(0xFFF9F9FF),
            onSurface = Color(0xFF1A1B20),
            surfaceVariant = Color(0xFFE1E2EC),
            onSurfaceVariant = Color(0xFF44474F),
            surfaceTint = Color(0xFF445E91),
            inverseSurface = Color(0xFF2F3036),
            inverseOnSurface = Color(0xFFF0F0F7),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF93000A),
            outline = Color(0xFF75777F),
            outlineVariant = Color(0xFFC4C6D0),
            scrim = Color(0xFF000000),
            surfaceBright = Color(0xFFF9F9FF),
            surfaceContainer = Color(0xFFEDEDF4),
            surfaceContainerHigh = Color(0xFFE8E7EF),
            surfaceContainerHighest = Color(0xFFE2E2E9),
            surfaceContainerLow = Color(0xFFF3F3FA),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFD9D9E0),
        )

    val dark: ColorScheme =
        darkColorScheme(
            primary = Color(0xFFADC6FF),
            onPrimary = Color(0xFF102F60),
            primaryContainer = Color(0xFF2B4678),
            onPrimaryContainer = Color(0xFFD8E2FF),
            inversePrimary = Color(0xFF445E91),
            secondary = Color(0xFFBFC6DC),
            onSecondary = Color(0xFF293041),
            secondaryContainer = Color(0xFF3F4759),
            onSecondaryContainer = Color(0xFFDBE2F9),
            tertiary = Color(0xFFDEBCDF),
            onTertiary = Color(0xFF402843),
            tertiaryContainer = Color(0xFF583E5B),
            onTertiaryContainer = Color(0xFFFBD7FC),
            background = Color(0xFF111318),
            onBackground = Color(0xFFE2E2E9),
            surface = Color(0xFF111318),
            onSurface = Color(0xFFE2E2E9),
            surfaceVariant = Color(0xFF44474F),
            onSurfaceVariant = Color(0xFFC4C6D0),
            surfaceTint = Color(0xFFADC6FF),
            inverseSurface = Color(0xFFE2E2E9),
            inverseOnSurface = Color(0xFF2F3036),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = Color(0xFF8E9099),
            outlineVariant = Color(0xFF44474F),
            scrim = Color(0xFF000000),
            surfaceBright = Color(0xFF37393E),
            surfaceContainer = Color(0xFF1E1F25),
            surfaceContainerHigh = Color(0xFF282A2F),
            surfaceContainerHighest = Color(0xFF33353A),
            surfaceContainerLow = Color(0xFF1A1B20),
            surfaceContainerLowest = Color(0xFF0C0E13),
            surfaceDim = Color(0xFF111318),
        )
}

/** Độ tương phản WCAG 2.x. */
object ColorContrast {
    private const val CHANNEL_MAX = 255.0
    private const val LINEAR_THRESHOLD = 0.04045
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA = 2.4
    private const val FLARE = 0.05

    /** Độ chói tương đối (0.0 đen … 1.0 trắng) của màu sRGB [argb]. */
    fun luminance(argb: Int): Double =
        0.2126 * linear(argb shr 16) + 0.7152 * linear(argb shr 8) + 0.0722 * linear(argb)

    /** Tỉ lệ tương phản (1.0 … 21.0), không phụ thuộc thứ tự. */
    fun ratio(
        a: Int,
        b: Int,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + FLARE) / (min(la, lb) + FLARE)
    }

    /** Kênh 8 bit thấp nhất của [channel] → giá trị tuyến tính 0..1. */
    private fun linear(channel: Int): Double {
        val c = (channel and 0xFF) / CHANNEL_MAX
        return if (c <= LINEAR_THRESHOLD) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / (1 + GAMMA_OFFSET)).pow(GAMMA)
    }
}
