package app.pixel.browser.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** Phần (nhóm) trong màn hình Cài đặt (spec 014). */
data class SettingsSection(
    val id: String,
    val title: @Composable () -> String,
    val order: Int,
)

/** Công cụ để trang cài đặt điều hướng/mở URL mà không biết về route (spec 014). */
interface SettingsPageScope {
    fun navigateBack()

    fun open(pageId: String)

    fun openUrl(url: String)
}

class SettingsPage(
    val id: String,
    val sectionId: String,
    val order: Int,
    val title: @Composable () -> String,
    val summary: (@Composable () -> String)? = null,
    val isPlaceholder: Boolean = false,
    val content: @Composable (SettingsPageScope) -> Unit,
)

object SettingsSections {
    const val SEARCH = "search"
    const val APPEARANCE = "appearance"
    const val PRIVACY = "privacy"
    const val SITE = "site"
    const val ABOUT = "about"

    /** Các phần mặc định theo thứ tự hiển thị (spec 014 AC8). */
    val defaults: List<SettingsSection> =
        listOf(
            SettingsSection(SEARCH, { stringResource(R.string.settings_section_search) }, 0),
            SettingsSection(APPEARANCE, { stringResource(R.string.settings_section_appearance) }, 1),
            SettingsSection(PRIVACY, { stringResource(R.string.settings_section_privacy) }, 2),
            SettingsSection(SITE, { stringResource(R.string.settings_section_site) }, 3),
            SettingsSection(ABOUT, { stringResource(R.string.settings_section_about) }, 4),
        )
}

object SettingsPageIds {
    const val SEARCH_ENGINE = "search_engine"
    const val THEME = "theme"
    const val ABOUT = "about"
    const val SITE_SETTINGS = "site_settings"
    const val CLEAR_DATA = "clear_data"
    const val SAFE_BROWSING = "safe_browsing"
    const val ACCESSIBILITY = "accessibility"
    const val DESKTOP_SITE = "desktop_site"
    const val PRIVATE_BROWSING = "private_browsing"
}

/** Registry dữ liệu của các trang cài đặt (spec 014 AC5–AC7). */
class SettingsRegistry(
    sections: List<SettingsSection>,
    pages: List<SettingsPage>,
) {
    private val resolved: List<SettingsPage>

    /** Các phần đã sắp theo `order`, mỗi phần kèm trang đã sắp theo `order` rồi `id`; phần rỗng bị bỏ. */
    val groups: List<Pair<SettingsSection, List<SettingsPage>>>

    init {
        val sectionIds = sections.map { it.id }
        require(sectionIds.distinct().size == sectionIds.size) { "Duplicate settings section ids: $sectionIds" }
        pages.forEach {
            require(
                it.sectionId in sectionIds,
            ) { "Unknown section '${it.sectionId}' for page '${it.id}'" }
        }
        val (placeholders, real) = pages.partition { it.isPlaceholder }
        require(real.map { it.id }.let { it.distinct().size == it.size }) { "Duplicate settings page ids" }
        require(placeholders.map { it.id }.let { it.distinct().size == it.size }) { "Duplicate placeholder ids" }
        val realIds = real.mapTo(HashSet()) { it.id }
        resolved = real + placeholders.filter { it.id !in realIds }
        val byOrder = compareBy<SettingsPage>({ it.order }, { it.id })
        groups =
            sections
                .sortedBy { it.order }
                .map { section -> section to resolved.filter { it.sectionId == section.id }.sortedWith(byOrder) }
                .filter { it.second.isNotEmpty() }
    }

    fun page(id: String): SettingsPage? = resolved.firstOrNull { it.id == id }
}
