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
)
