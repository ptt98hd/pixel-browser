package app.pixel.browser.feature.bookmarks

object BookmarksTestTags {
    const val SCREEN = "bookmarks_screen"
    const val TITLE = "bookmarks_title"
    const val UP = "bookmarks_up"
    const val CLOSE = "bookmarks_close"
    const val SEARCH = "bookmarks_search"
    const val SEARCH_FIELD = "bookmarks_search_field"
    const val EMPTY = "bookmarks_empty"
    const val NEW_FOLDER = "bookmarks_new_folder"
    const val UNDO = "bookmarks_undo"
    const val IMPORT = "bookmarks_import"
    const val EXPORT = "bookmarks_export"
    const val IMPORT_RESULT = "bookmarks_import_result"
    const val MENU_EDIT = "bookmark_menu_edit"
    const val MENU_OPEN_NEW_TAB = "bookmark_menu_open_new_tab"
    const val MENU_COPY = "bookmark_menu_copy"
    const val MENU_MOVE = "bookmark_menu_move"
    const val MENU_DELETE = "bookmark_menu_delete"
    const val MENU_RENAME = "bookmark_menu_rename"

    /** Hộp thoại tạo/đổi tên thư mục (dùng chung): trường tên, nút xác nhận, thông báo lỗi. */
    const val FOLDER_DIALOG = "bookmarks_folder_dialog"
    const val FOLDER_NAME_FIELD = "bookmarks_folder_name_field"
    const val FOLDER_DIALOG_CONFIRM = "bookmarks_folder_dialog_confirm"
    const val FOLDER_DIALOG_ERROR = "bookmarks_folder_dialog_error"

    /** Bộ chọn thư mục đích khi "Di chuyển"; mục dùng [folderOption]. */
    const val MOVE_DIALOG = "bookmarks_move_dialog"

    // Hộp thoại sửa (AC20)
    const val EDIT_DIALOG = "bookmark_dialog"
    const val EDIT_TITLE_FIELD = "bookmark_title_field"
    const val EDIT_URL_FIELD = "bookmark_url_field"
    const val EDIT_FOLDER_PICKER = "bookmark_folder_picker"
    const val EDIT_SAVE = "bookmark_save"
    const val EDIT_ERROR = "bookmark_error"
    const val EDIT_REMOVE = "bookmark_remove"
    const val EDIT_CANCEL = "bookmark_cancel"

    fun folder(id: Long): String = "bookmark_folder_$id"

    fun item(id: Long): String = "bookmark_item_$id"

    fun folderOption(id: Long): String = "bookmark_folder_option_$id"
}
