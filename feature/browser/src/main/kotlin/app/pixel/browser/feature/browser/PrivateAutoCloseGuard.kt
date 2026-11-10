package app.pixel.browser.feature.browser

import app.pixel.browser.core.data.privacy.PrivateAutoClose

/**
 * Quyết định đóng tab ẩn danh sau khi rời app (spec 016 AC15). Kiểm lúc quay lại, không có timer nền.
 * [clock] là đồng hồ đơn điệu (ms), [autoClose] đọc cài đặt hiện tại.
 */
class PrivateAutoCloseGuard(
    private val clock: () -> Long,
    private val autoClose: () -> PrivateAutoClose,
) {
    private var backgroundAt: Long? = null

    fun onBackground(
        hasPrivateTabs: Boolean,
        isChangingConfigurations: Boolean,
    ) {
        backgroundAt = if (hasPrivateTabs && !isChangingConfigurations) clock() else null
    }

    /** true = phải đóng tab ẩn danh (chỉ một lần cho mỗi lần rời app). */
    fun onForeground(): Boolean {
        val since = backgroundAt ?: return false
        backgroundAt = null
        val minutes = autoClose().minutes
        return minutes > 0 && clock() - since >= minutes * MS_PER_MINUTE
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
