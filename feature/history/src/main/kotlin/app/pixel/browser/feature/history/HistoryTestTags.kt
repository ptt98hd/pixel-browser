package app.pixel.browser.feature.history

import app.pixel.browser.core.history.HistoryRange
import java.time.LocalDate

object HistoryTestTags {
    const val SCREEN = "history_screen"
    const val SEARCH = "history_search"
    const val SEARCH_CLEAR = "history_search_clear"
    const val MENU_OPEN_NEW_TAB = "history_menu_open_new_tab"
    const val MENU_COPY = "history_menu_copy"
    const val MENU_DELETE = "history_menu_delete"
    const val CLEAR_BUTTON = "history_clear_button"
    const val CLEAR_DIALOG = "history_clear_dialog"
    const val CLEAR_CONFIRM = "history_clear_confirm"
    const val CLEAR_CANCEL = "history_clear_cancel"
    const val EMPTY = "history_empty"
    const val LOAD_MORE = "history_load_more"
    const val CLOSE = "history_close"

    fun day(date: LocalDate): String = "history_day_$date"

    fun item(id: Long): String = "history_item_$id"

    fun clearRange(range: HistoryRange): String = "history_clear_range_${range.name}"
}
