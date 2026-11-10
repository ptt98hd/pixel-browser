package app.pixel.browser

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import app.pixel.browser.feature.settings.AboutPage
import app.pixel.browser.feature.settings.AppInfo
import app.pixel.browser.feature.settings.AppearancePage
import app.pixel.browser.feature.settings.DesktopSiteSettingsPage
import app.pixel.browser.feature.settings.PrivateBrowsingPage
import app.pixel.browser.feature.settings.R
import app.pixel.browser.feature.settings.SearchEnginePage
import app.pixel.browser.feature.settings.SettingsPage
import app.pixel.browser.feature.settings.SettingsPageIds
import app.pixel.browser.feature.settings.SettingsPlaceholders
import app.pixel.browser.feature.settings.SettingsRegistry
import app.pixel.browser.feature.settings.SettingsSections

/**
 * Khai báo các trang cài đặt của app (spec 014): mỗi spec sau thêm đúng một dòng vào danh sách trang thật
 * (trang giữ chỗ cùng id tự bị thay thế).
 */
fun settingsRegistry(
    app: PixelBrowserApplication,
    info: AppInfo,
): SettingsRegistry =
    SettingsRegistry(
        sections = SettingsSections.defaults,
        pages =
            SettingsPlaceholders.all +
                listOf(
                    searchEnginePage(app),
                    appearancePage(app),
                    desktopSitePage(app),
                    privateBrowsingPage(app),
                    aboutPage(info),
                ),
    )

private fun searchEnginePage(app: PixelBrowserApplication) =
    SettingsPage(
        id = SettingsPageIds.SEARCH_ENGINE,
        sectionId = SettingsSections.SEARCH,
        order = 0,
        title = { stringResource(R.string.settings_page_search_engine) },
        summary = {
            val engine by app.searchEngine.collectAsState()
            engine.name
        },
        content = {
            val settings by app.searchSettings.collectAsState()
            SearchEnginePage(settings, onChange = app::updateSearch)
        },
    )

private fun appearancePage(app: PixelBrowserApplication) =
    SettingsPage(
        id = SettingsPageIds.THEME,
        sectionId = SettingsSections.APPEARANCE,
        order = 0,
        title = { stringResource(R.string.settings_page_theme) },
        content = {
            val settings by app.themeSettings.collectAsState()
            AppearancePage(settings, onChange = { next -> app.updateTheme { next } })
        },
    )

private fun desktopSitePage(app: PixelBrowserApplication) =
    SettingsPage(
        id = SettingsPageIds.DESKTOP_SITE,
        sectionId = SettingsSections.SITE,
        order = 1,
        title = { stringResource(R.string.settings_page_desktop_site) },
        content = { DesktopSiteSettingsPage(app.desktopSites) },
    )

private fun privateBrowsingPage(app: PixelBrowserApplication) =
    SettingsPage(
        id = SettingsPageIds.PRIVATE_BROWSING,
        sectionId = SettingsSections.PRIVACY,
        order = 2,
        title = { stringResource(R.string.settings_page_private_browsing) },
        content = {
            val settings by app.privacySettings.collectAsState()
            PrivateBrowsingPage(
                settings,
                privateAvailable = app.privateSpace.isAvailable,
                onChange = { next -> app.updatePrivacy { next } },
            )
        },
    )

private fun aboutPage(info: AppInfo) =
    SettingsPage(
        id = SettingsPageIds.ABOUT,
        sectionId = SettingsSections.ABOUT,
        order = 0,
        title = { stringResource(R.string.settings_page_about) },
        content = { scope -> AboutPage(info, scope) },
    )
