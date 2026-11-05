package app.pixel.browser.core.tabs

/** URL của tab home (trang mới). Không phải URL web nên không được `loadUrl` vào engine. */
const val HOME_URL = "about:blank"

/** Một tab. [id] duy nhất và ổn định qua process death. Tab [isPrivate] không bao giờ được lưu xuống đĩa. */
data class Tab(
    val id: String,
    val url: String,
    val title: String = "",
    val isPrivate: Boolean = false,
)
