package app.pixel.browser.feature.settings

object SettingsTestTags {
    const val SCREEN = "settings_screen"
    const val TITLE = "settings_title"
    const val BACK = "settings_back"

    fun section(id: String) = "settings_section_$id"

    fun item(id: String) = "settings_item_$id"

    fun placeholder(id: String) = "settings_placeholder_$id"

    fun page(id: String) = "settings_page_$id"

    fun searchEngineOption(id: String) = "search_engine_option_$id"

    const val CUSTOM_NAME = "search_custom_name"
    const val CUSTOM_TEMPLATE = "search_custom_template"
    const val CUSTOM_PREVIEW = "search_custom_preview"
    const val CUSTOM_ERROR = "search_custom_error"
    const val CUSTOM_HTTP_WARNING = "search_custom_http_warning"
    const val CUSTOM_SAVE = "search_custom_save"

    fun appearanceMode(name: String) = "appearance_mode_$name"

    const val APPEARANCE_DYNAMIC = "appearance_dynamic"
    const val ABOUT_VERSION = "about_version"
    const val ABOUT_LICENSE = "about_license"
    const val ABOUT_SOURCE = "about_source"
    const val ABOUT_LICENSES_LIST = "about_licenses_list"

    // Spec 015
    const val DESKTOP_DEFAULT = "settings_desktop_default"
    const val DESKTOP_OVERRIDES = "settings_desktop_overrides"
    const val DESKTOP_CLEAR = "settings_desktop_clear"

    fun aboutLicense(index: Int) = "about_license_$index"
}
