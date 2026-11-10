package app.pixel.browser.core.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings

/** Tối hay sáng theo [mode]; [ThemeMode.System] đi theo [systemDark] (spec 011 AC1). */
fun resolveDarkTheme(
    mode: ThemeMode,
    systemDark: Boolean,
    /** Tab ẩn danh (spec 016 AC21): luôn tối, bỏ qua [mode] và hệ thống. */
    private: Boolean = false,
): Boolean =
    when {
        private -> true
        mode == ThemeMode.System -> systemDark
        mode == ThemeMode.Light -> false
        else -> true
    }

/** Color scheme: dynamic (hình nền) nếu [dynamicColor], ngược lại [PixelColors] (spec 011 AC6). */
fun pixelColorScheme(
    context: Context,
    dark: Boolean,
    dynamicColor: Boolean,
): ColorScheme =
    when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> PixelColors.dark
        else -> PixelColors.light
    }

/**
 * Theme của Pixel: color scheme theo [settings], [PixelShapes], [PixelTypography] và [LocalReducedMotion]
 * (spec 011 AC6, AC7). Reduced motion đọc khi vào composition và mỗi lần `ON_RESUME` (người dùng có thể đổi
 * cài đặt hệ thống rồi quay lại).
 */
@Composable
fun PixelTheme(
    settings: ThemeSettings = ThemeSettings(),
    /** Tab ẩn danh được chọn (spec 016 AC21): dùng [PrivateColorScheme] cố định, bỏ qua dynamic color/mode. */
    private: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = resolveDarkTheme(settings.mode, isSystemInDarkTheme())
    val reducedMotion = rememberReducedMotion(context)
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = if (private) PrivateColorScheme else pixelColorScheme(context, dark, settings.dynamicColor),
            shapes = PixelShapes,
            typography = PixelTypography,
            content = content,
        )
    }
}

@Composable
private fun rememberReducedMotion(context: Context): Boolean {
    var reduced by remember(context) { mutableStateOf(isReducedMotion(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) reduced = isReducedMotion(context)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return reduced
}

private fun isReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
