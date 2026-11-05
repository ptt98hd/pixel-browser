package app.pixel.browser.feature.browser

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
    const val TAB_SHEET = "tab_sheet"
    const val TAB_SHEET_NEW = "tab_sheet_new"
    const val ENGINE_CONTAINER = "engine_container"
    const val ENGINE_PLACEHOLDER = "engine_placeholder"

    fun tabItem(id: String): String = "tab_item_$id"

    fun tabClose(id: String): String = "tab_close_$id"

    fun suggestion(index: Int): String = "suggestion_$index"
}
