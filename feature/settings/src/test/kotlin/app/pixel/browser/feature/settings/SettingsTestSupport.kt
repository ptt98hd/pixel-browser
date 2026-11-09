package app.pixel.browser.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction

fun section(
    id: String,
    order: Int,
) = SettingsSection(id, { id.replaceFirstChar { it.uppercase() } }, order)

fun page(
    id: String,
    sectionId: String,
    order: Int = 0,
    placeholder: Boolean = false,
    summary: (@Composable () -> String)? = null,
    content: @Composable (SettingsPageScope) -> Unit = {},
) = SettingsPage(
    id = id,
    sectionId = sectionId,
    order = order,
    title = { "Title $id" },
    summary = summary,
    isPlaceholder = placeholder,
    content = content,
)

/** Registry giống danh sách mặc định của app (spec 014 AC8) với nội dung trang do test cung cấp. */
fun defaultLikeRegistry(
    searchSummary: String = "DuckDuckGo",
    contentOf: (
        String,
    ) -> @Composable (SettingsPageScope) -> Unit = { id -> { androidx.compose.material3.Text("content-$id") } },
): SettingsRegistry =
    SettingsRegistry(
        sections =
            listOf(
                section(SettingsSections.SEARCH, 0),
                section(SettingsSections.APPEARANCE, 1),
                section(SettingsSections.PRIVACY, 2),
                section(SettingsSections.SITE, 3),
                section(SettingsSections.ABOUT, 4),
            ),
        pages =
            listOf(
                page(SettingsPageIds.SEARCH_ENGINE, SettingsSections.SEARCH, summary = {
                    searchSummary
                }, content = contentOf("search_engine")),
                page(SettingsPageIds.THEME, SettingsSections.APPEARANCE, content = contentOf("theme")),
                page(SettingsPageIds.CLEAR_DATA, SettingsSections.PRIVACY, 0, placeholder = true),
                page(SettingsPageIds.SAFE_BROWSING, SettingsSections.PRIVACY, 1, placeholder = true),
                page(SettingsPageIds.SITE_SETTINGS, SettingsSections.SITE, 0, placeholder = true),
                page(SettingsPageIds.DESKTOP_SITE, SettingsSections.SITE, 1, placeholder = true),
                page(SettingsPageIds.ABOUT, SettingsSections.ABOUT, content = contentOf("about")),
            ),
    )

fun SemanticsNodeInteraction.textValue(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.Text) { emptyList() }
        .joinToString(" ") { it.text }

fun SemanticsNodeInteraction.editableText(): String =
    fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.EditableText) {
            androidx.compose.ui.text
                .AnnotatedString("")
        }.text

fun SemanticsNodeInteraction.contentDescriptions(): List<String> =
    fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

/** [SettingsPageScope] ghi lại lời gọi, dùng để test trang độc lập. */
class RecordingScope : SettingsPageScope {
    var backs = 0
    val opened = mutableListOf<String>()
    val urls = mutableListOf<String>()

    override fun navigateBack() {
        backs++
    }

    override fun open(pageId: String) {
        opened += pageId
    }

    override fun openUrl(url: String) {
        urls += url
    }
}
