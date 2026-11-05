package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.SecurityState
import app.pixel.browser.core.omnibox.Suggestion

/** Một mục trong tab switcher (spec 005, AC27). */
data class TabItem(
    val id: String,
    val title: String,
    val url: String,
)

/** Trạng thái chrome trình duyệt (spec 005). [url]/[title]/... lấy trực tiếp từ session của tab được chọn. */
data class BrowserUiState(
    val url: String = "",
    val title: String = "",
    val displayHost: String = "",
    val isLoading: Boolean = false,
    /** 0..100 */
    val progress: Int = 0,
    val securityState: SecurityState = SecurityState.Unknown,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val tabCount: Int = 0,
    val tabs: List<TabItem> = emptyList(),
    val selectedTabId: String? = null,
    val isEditingUrl: Boolean = false,
    val suggestions: List<Suggestion> = emptyList(),
    val message: String? = null,
)
