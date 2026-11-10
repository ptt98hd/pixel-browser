package app.pixel.browser.feature.browser

import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.omnibox.Suggestion

/** Callback hành động của chrome (spec 005). Mặc định no-op để test composable độc lập. */
data class BrowserActions(
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onNewTab: () -> Unit = {},
    val onScrollToTop: () -> Unit = {},
    val onScrollToBottom: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onStartEditing: () -> Unit = {},
    val onCancelEditing: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onSuggestionClick: (Suggestion) -> Unit = {},
    val onSelectTab: (String) -> Unit = {},
    val onCloseTab: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** Mở hộp thoại/màn hình đặt trình duyệt mặc định (spec 006 AC19). */
    val onSetDefaultBrowser: () -> Unit = {},
    /** Trả lời hộp thoại mở app ngoài: (tabId, requestId, open) – spec 006 AC24. */
    val onExternalAppDecision: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    /** Back ở gốc lịch sử của tab mở từ intent: đóng tab và quay về app gọi (spec 006 AC14). */
    val onReturnToCaller: () -> Unit = {},
    /** Người dùng đổi theme trong `ThemeDialog` (spec 011). */
    val onThemeChange: (ThemeSettings) -> Unit = {},
    /** Thoát video toàn màn hình (spec 007 AC20). */
    val onExitFullscreen: () -> Unit = {},
    /** Mở cài đặt quyền của app khi hệ thống từ chối quyền trang xin (spec 007, edge "Không hỏi lại"). */
    val onOpenAppSettings: () -> Unit = {},
    /** Mở URL web (vd. shortcut trang chủ) trong tab đang chọn (spec 010). */
    val onOpenUrl: (String) -> Unit = {},
    /** Mở tab switcher (spec 009 AC8, AC12): ViewModel chụp thumbnail tab hiện tại. */
    val onOpenTabSwitcher: () -> Unit = {},
    /** Hoàn tác đóng tab gần nhất (spec 009 AC15). */
    val onUndoCloseTab: () -> Unit = {},
    /** Đóng tất cả tab (spec 009 AC17). */
    val onCloseAllTabs: () -> Unit = {},
    /** Vuốt bottom bar / accessibility: đổi sang tab kề, -1 = trước, +1 = sau (spec 008 AC12–AC14). */
    val onSelectAdjacentTab: (Int) -> Unit = {},
    /** Mở màn hình Lịch sử (spec 012). */
    val onOpenHistory: () -> Unit = {},
    /** Mở màn hình Cài đặt (spec 014). */
    val onOpenSettings: () -> Unit = {},
    /** Mở màn hình Dấu trang (spec 013). */
    val onOpenBookmarks: () -> Unit = {},
    /** Menu "Thêm/Sửa dấu trang" (spec 013 AC18/AC19). */
    val onToggleBookmark: () -> Unit = {},
    /** Mở hộp thoại sửa dấu trang [id] (spec 013 AC18/AC19). */
    val onEditBookmark: (Long) -> Unit = {},
    /** "Hoàn tác" trên snackbar sau khi xóa dấu trang từ hộp thoại sửa (spec 013 AC20). */
    val onUndoBookmarkRemoved: () -> Unit = {},
    /** Menu "Tìm trong trang" (spec 015 B04). */
    val onOpenFind: () -> Unit = {},
    val onFindQueryChange: (String) -> Unit = {},
    /** true = kết quả kế tiếp, false = kết quả trước. */
    val onFindNext: (Boolean) -> Unit = {},
    val onCloseFind: () -> Unit = {},
    /** Menu "Trang web máy tính" (spec 015 B05). */
    val onToggleDesktopSite: () -> Unit = {},
)
