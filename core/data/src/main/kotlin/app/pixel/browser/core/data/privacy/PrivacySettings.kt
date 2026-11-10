package app.pixel.browser.core.data.privacy

import kotlinx.coroutines.flow.Flow

/** Tự đóng tab ẩn danh sau khi rời app (spec 016); [minutes] = 0 nghĩa là không bao giờ. */
enum class PrivateAutoClose(
    val minutes: Int,
) {
    Never(0),
    Minutes5(5),
    Minutes30(30),
}

/** Cài đặt riêng tư (spec 016); spec 017 mở rộng bằng các trường có mặc định. */
data class PrivacySettings(
    val blockPrivateScreenshots: Boolean = true,
    val privateAutoClose: PrivateAutoClose = PrivateAutoClose.Never,
)

interface PrivacySettingsStore {
    val settings: Flow<PrivacySettings>

    suspend fun update(transform: (PrivacySettings) -> PrivacySettings)
}
