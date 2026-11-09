package app.pixel.browser.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Trang giữ chỗ "Sắp có" cho các spec sau (spec 014). Trang thật cùng id thay thế tự động
 * (xem [SettingsRegistry]); `accessibility` (B14) chưa có dòng giữ chỗ.
 */
object SettingsPlaceholders {
    val all: List<SettingsPage> =
        listOf(
            placeholder(SettingsPageIds.CLEAR_DATA, SettingsSections.PRIVACY, 0) {
                stringResource(R.string.settings_page_clear_data)
            },
            placeholder(SettingsPageIds.SAFE_BROWSING, SettingsSections.PRIVACY, 1) {
                stringResource(R.string.settings_page_safe_browsing)
            },
            placeholder(SettingsPageIds.SITE_SETTINGS, SettingsSections.SITE, 0) {
                stringResource(R.string.settings_page_site_settings)
            },
            placeholder(SettingsPageIds.DESKTOP_SITE, SettingsSections.SITE, 1) {
                stringResource(R.string.settings_page_desktop_site)
            },
        )

    private fun placeholder(
        id: String,
        sectionId: String,
        order: Int,
        title: @Composable () -> String,
    ) = SettingsPage(id, sectionId, order, title, isPlaceholder = true, content = {})
}
