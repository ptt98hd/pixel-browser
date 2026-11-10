package app.pixel.browser

import app.pixel.browser.feature.settings.AppInfo
import app.pixel.browser.feature.settings.SettingsPageIds
import app.pixel.browser.feature.settings.SettingsSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Spec 014 AC8 – danh sách trang cài đặt mặc định của app. */
@RunWith(RobolectricTestRunner::class)
class SettingsPagesTest {
    private val info = AppInfo("1.0.0", 1L, "https://github.com/example/pixel-browser")

    private fun registry() = settingsRegistry(RuntimeEnvironment.getApplication() as PixelBrowserApplication, info)

    @Test
    fun ac8_sectionsInOrder_withExpectedPages() {
        val layout = registry().groups.map { (s, pages) -> s.id to pages.map { it.id } }
        // Spec 016 AC22: thêm trang private_browsing vào phần privacy (thứ tự trong phần do implementer chọn).
        assertEquals(
            listOf(
                SettingsSections.SEARCH,
                SettingsSections.APPEARANCE,
                SettingsSections.PRIVACY,
                SettingsSections.SITE,
                SettingsSections.ABOUT,
            ),
            layout.map { it.first },
        )
        val byId = layout.toMap()
        assertEquals(listOf(SettingsPageIds.SEARCH_ENGINE), byId.getValue(SettingsSections.SEARCH))
        assertEquals(listOf(SettingsPageIds.THEME), byId.getValue(SettingsSections.APPEARANCE))
        assertEquals(
            setOf(SettingsPageIds.CLEAR_DATA, SettingsPageIds.SAFE_BROWSING, SettingsPageIds.PRIVATE_BROWSING),
            byId.getValue(SettingsSections.PRIVACY).toSet(),
        )
        assertEquals(3, byId.getValue(SettingsSections.PRIVACY).size)
        assertEquals(
            listOf(SettingsPageIds.SITE_SETTINGS, SettingsPageIds.DESKTOP_SITE),
            byId.getValue(SettingsSections.SITE),
        )
        assertEquals(listOf(SettingsPageIds.ABOUT), byId.getValue(SettingsSections.ABOUT))
    }

    @Test
    fun ac8_realPagesAreNotPlaceholders_placeholdersAreFlagged() {
        val r = registry()
        // Spec 015 AC22: trang desktop_site thật thay trang giữ chỗ của spec 014.
        listOf(
            SettingsPageIds.SEARCH_ENGINE,
            SettingsPageIds.THEME,
            SettingsPageIds.DESKTOP_SITE,
            SettingsPageIds.ABOUT,
            SettingsPageIds.PRIVATE_BROWSING,
        ).forEach {
            assertEquals(it, false, r.page(it)!!.isPlaceholder)
        }
        listOf(
            SettingsPageIds.CLEAR_DATA,
            SettingsPageIds.SAFE_BROWSING,
            SettingsPageIds.SITE_SETTINGS,
        ).forEach { assertEquals(it, true, r.page(it)!!.isPlaceholder) }
    }

    @Test
    fun ac8_pageIdsAreUnique() {
        val ids = registry().groups.flatMap { it.second }.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.size == 8)
    }

    @Test
    fun ac8_accessibilityPlaceholder_isNotListedYet() {
        // B14 chưa có spec: ACCESSIBILITY chỉ là id dành trước, chưa có dòng giữ chỗ.
        assertEquals(null, registry().page(SettingsPageIds.ACCESSIBILITY))
    }
}
