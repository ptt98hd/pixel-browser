package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.SecurityState
import app.pixel.browser.core.omnibox.Suggestion

/** Một mục trong tab switcher (spec 005, AC27). */
data class TabItem(
    val id: String,
    val title: String,
    val url: String,
    /** Tab đang ở trang chủ native (spec 009 AC13: thẻ hiển thị placeholder, tiêu đề "Tab mới"). */
    val isHome: Boolean = false,
    /** Tab ẩn danh (spec 009 AC8: thumbnail không ghi đĩa). */
    val isPrivate: Boolean = false,
)

/** Trạng thái thanh tìm trong trang (spec 015). [current] 1-based, 0 khi [total] == 0. */
data class FindUiState(
    val query: String,
    val current: Int,
    val total: Int,
    val isSearching: Boolean,
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
    /** Tab đang chọn hiển thị trang chủ native thay cho engine (spec 010). */
    val isHome: Boolean = false,
    /** Có thể lưu dấu trang: URL là `isValidWebUrl`, không ở home, có kho dấu trang (spec 013). */
    val canBookmark: Boolean = false,
    /** Trang hiện tại đã có dấu trang (so theo `urlKey`). */
    val isBookmarked: Boolean = false,
    val bookmarkId: Long? = null,
    /** Có thể mở thanh tìm: tab web, không fullscreen (spec 015 B04). */
    val canFind: Boolean = false,
    /** Thanh tìm trong trang; null = đóng (spec 015 B04). */
    val find: FindUiState? = null,
    /** Có thể bật/tắt trang web máy tính: URL web hợp lệ, không ở home (spec 015 B05). */
    val canToggleDesktopSite: Boolean = false,
    /** Trang đang xem ở chế độ máy tính (spec 015 B05). */
    val isDesktopSite: Boolean = false,
    /** Tab đang chọn là tab ẩn danh (spec 016). */
    val selectedIsPrivate: Boolean = false,
    /** Có thể mở tab ẩn danh (`MULTI_PROFILE` được hỗ trợ; spec 016). */
    val canOpenPrivate: Boolean = false,
    /** Trình chuyển tab đang hiển thị nhóm ẩn danh (spec 016 AC19). */
    val switcherShowsPrivate: Boolean = false,
    /** Lần đóng tab gần nhất hoàn tác được; false khi đóng tab ẩn danh cuối (spec 016 AC20). */
    val lastCloseUndoable: Boolean = true,
)
