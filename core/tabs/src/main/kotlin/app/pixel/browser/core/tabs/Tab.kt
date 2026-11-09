package app.pixel.browser.core.tabs

/**
 * URL của tab đang ở trang chủ native (spec 010). Chỉ là trạng thái trong [TabManager]: không phải URL web và
 * không bao giờ được `loadUrl` vào engine.
 */
const val HOME_URL = "pixel://home"

/** Một tab. [id] duy nhất và ổn định qua process death. Tab [isPrivate] không bao giờ được lưu xuống đĩa. */
data class Tab(
    val id: String,
    val url: String,
    val title: String = "",
    val isPrivate: Boolean = false,
    /** Tab rời home bằng [TabManager.loadUrl]: Back ở gốc lịch sử engine quay về home (spec 010). */
    val backToHome: Boolean = false,
) {
    /** Tab đang hiển thị trang chủ native (spec 010). */
    val isHome: Boolean get() = url == HOME_URL
}
