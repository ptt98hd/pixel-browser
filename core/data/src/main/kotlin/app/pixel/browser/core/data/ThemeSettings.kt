package app.pixel.browser.core.data

import kotlinx.coroutines.flow.Flow

/** Chế độ sáng/tối người dùng chọn (spec 011). */
enum class ThemeMode { System, Light, Dark }

/** Cài đặt theme được lưu (spec 011). */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
)

/** Nguồn [ThemeSettings] bền vững. [settings] phát giá trị hiện tại rồi mỗi lần đổi. */
interface ThemeSettingsStore {
    val settings: Flow<ThemeSettings>

    suspend fun update(transform: (ThemeSettings) -> ThemeSettings)
}
