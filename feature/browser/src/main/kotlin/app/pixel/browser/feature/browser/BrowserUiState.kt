package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineRequest
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
    /** Hiện mục menu "Đặt làm trình duyệt mặc định" (spec 006 AC18). */
    val showSetDefaultBrowser: Boolean = false,
    /** Request mở app ngoài đang chờ người dùng xác nhận (spec 006 AC24). */
    val externalPrompt: EngineRequest.ExternalApp? = null,
    /** Tab sở hữu [externalPrompt]; quyết định của người dùng gắn với tab này (spec 006). */
    val externalPromptTabId: String? = null,
    /** Tab đang chọn được mở từ intent ngoài: Back ở gốc lịch sử quay về app gọi (spec 006 AC14). */
    val isExternalTab: Boolean = false,
    /** Request UI đầu tiên (FIFO) của tab đang chọn mà người dùng phải trả lời (spec 007 AC13). */
    val pendingRequest: EngineRequest? = null,
    /** Tab đang chọn đang phát video toàn màn hình (spec 007 AC20). */
    val isFullscreen: Boolean = false,
)
