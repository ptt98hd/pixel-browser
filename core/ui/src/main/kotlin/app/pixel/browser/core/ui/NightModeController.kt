package app.pixel.browser.core.ui

import android.app.UiModeManager
import android.content.Context
import app.pixel.browser.core.data.ThemeMode

/** Áp chế độ sáng/tối cho app (spec 011 AC12). */
fun interface NightModeController {
    fun apply(mode: ThemeMode)
}

/**
 * [NightModeController] qua `UiModeManager.setApplicationNightMode`: Dark → `MODE_NIGHT_YES`, Light →
 * `MODE_NIGHT_NO`, System → `MODE_NIGHT_AUTO`. Hệ thống lưu lựa chọn và áp cho cửa sổ ngay từ khi khởi động.
 * [setNightMode] cho phép test thay lời gọi hệ thống.
 */
class UiModeNightModeController(
    context: Context,
    private val setNightMode: (Int) -> Unit = defaultSetNightMode(context),
) : NightModeController {
    override fun apply(mode: ThemeMode) {
        setNightMode(
            when (mode) {
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}

private fun defaultSetNightMode(context: Context): (Int) -> Unit {
    val appContext = context.applicationContext ?: context
    return { mode -> appContext.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode) }
}
