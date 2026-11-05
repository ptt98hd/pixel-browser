package app.pixel.browser.core.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Token motion lò xo theo M3 Expressive (spec 011 AC2). "Spatial" cho vị trí/kích thước/hình (có nảy nhẹ),
 * "effects" cho màu/độ mờ (không nảy).
 */
object PixelMotion {
    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> effectsFast(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)

    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> effectsSlow(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}

/** `true` khi người dùng tắt animation (ANIMATOR_DURATION_SCALE == 0); do [PixelTheme] cung cấp (spec 011 AC7). */
val LocalReducedMotion: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** [spec] hoặc `snap()` nếu [LocalReducedMotion] (spec 011 AC7). */
@Composable
fun <T> pixelSpring(spec: SpringSpec<T>): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else spec
