package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.SitePermission

/** Test tag ổn định cho chrome trình duyệt (spec 005). Test dựa vào tag, không dựa vào chuỗi hiển thị. */
object BrowserTestTags {
    const val URL_FIELD = "url_field"
    const val URL_DISPLAY = "url_display"
    const val SECURITY_SECURE = "security_secure"
    const val SECURITY_INSECURE = "security_insecure"
    const val PROGRESS = "progress"
    const val QR_BUTTON = "qr_button"
    const val BTN_BACK = "btn_back"
    const val BTN_FORWARD = "btn_forward"
    const val BTN_HOME = "btn_home"
    const val BTN_TABS = "btn_tabs"
    const val TAB_COUNT = "tab_count"
    const val BTN_MENU = "btn_menu"
    const val MENU_RELOAD = "menu_reload"
    const val MENU_NEW_TAB = "menu_new_tab"
    const val MENU_SHARE = "menu_share"
    const val MENU_SET_DEFAULT = "menu_set_default"
    const val EXTERNAL_DIALOG = "external_dialog"
    const val EXTERNAL_OPEN = "external_open"
    const val EXTERNAL_CANCEL = "external_cancel"
    const val MENU_THEME = "menu_theme"
    const val MENU_HISTORY = "menu_history"
    const val MENU_SETTINGS = "menu_settings"
    const val THEME_DIALOG = "theme_dialog"
    const val THEME_MODE_SYSTEM = "theme_mode_system"
    const val THEME_MODE_LIGHT = "theme_mode_light"
    const val THEME_MODE_DARK = "theme_mode_dark"
    const val THEME_DYNAMIC = "theme_dynamic"
    const val TAB_SWITCHER = "tab_switcher"
    const val TAB_SWITCHER_NEW = "tab_switcher_new"
    const val TAB_SWITCHER_CLOSE_ALL = "tab_switcher_close_all"
    const val CLOSE_ALL_DIALOG = "close_all_dialog"
    const val CLOSE_ALL_CONFIRM = "close_all_confirm"
    const val CLOSE_ALL_CANCEL = "close_all_cancel"
    const val TAB_UNDO = "tab_undo"
    const val ENGINE_CONTAINER = "engine_container"
    const val ENGINE_PLACEHOLDER = "engine_placeholder"

    // Spec 007
    const val JS_DIALOG = "js_dialog"
    const val JS_DIALOG_TITLE = "js_dialog_title"
    const val JS_DIALOG_MESSAGE = "js_dialog_message"
    const val JS_DIALOG_INPUT = "js_dialog_input"
    const val JS_DIALOG_OK = "js_dialog_ok"
    const val JS_DIALOG_CANCEL = "js_dialog_cancel"
    const val JS_DIALOG_SUPPRESS = "js_dialog_suppress"
    const val PERMISSION_DIALOG = "permission_dialog"
    const val PERMISSION_ALLOW = "permission_allow"
    const val PERMISSION_BLOCK = "permission_block"
    const val AUTH_DIALOG = "auth_dialog"
    const val AUTH_USERNAME = "auth_username"
    const val AUTH_PASSWORD = "auth_password"
    const val AUTH_OK = "auth_ok"
    const val AUTH_CANCEL = "auth_cancel"
    const val AUTH_INSECURE_WARNING = "auth_insecure_warning"
    const val DOWNLOAD_DIALOG = "download_dialog"

    /** Quyết định 2026-11-05: tải bằng `DownloadManager` hệ thống thay cho "mở bằng app khác". */
    const val DOWNLOAD_START = "download_start"
    const val DOWNLOAD_CANCEL = "download_cancel"
    const val FULLSCREEN_CONTAINER = "fullscreen_container"

    // Spec 008
    const val BOTTOM_BAR = "bottom_bar"
    const val EDGE_SWIPE_LEFT = "edge_swipe_left"
    const val EDGE_SWIPE_RIGHT = "edge_swipe_right"
    const val EDGE_SWIPE_INDICATOR = "edge_swipe_indicator"
    const val BACK_GESTURE_INDICATOR = "back_gesture_indicator"
    const val PULL_REFRESH_INDICATOR = "pull_refresh_indicator"

    fun tabItem(id: String): String = "tab_item_$id"

    fun tabClose(id: String): String = "tab_close_$id"

    fun tabThumb(id: String): String = "tab_thumb_$id"

    fun tabThumbPlaceholder(id: String): String = "tab_thumb_placeholder_$id"

    fun tabFavicon(id: String): String = "tab_favicon_$id"

    fun tabFaviconFallback(id: String): String = "tab_favicon_fallback_$id"

    fun suggestion(index: Int): String = "suggestion_$index"

    /** `permission_item_<Camera|Microphone|Location>` (spec 007 AC15). */
    fun permissionItem(permission: SitePermission): String = "permission_item_${permission.name}"
}
