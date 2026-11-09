package app.pixel.browser.core.data.home

import app.pixel.browser.core.omnibox.OmniboxClassifier
import app.pixel.browser.core.omnibox.OmniboxResult
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.UrlDisplay

/** Một shortcut trên trang chủ (spec 010). [url] đã chuẩn hóa (http/https). */
data class Shortcut(
    val id: String,
    val title: String,
    val url: String,
)

/** Nền trang chủ (spec 010). */
sealed interface HomeBackground {
    /** Màu surface của theme. */
    data object Dynamic : HomeBackground

    /** Một màu trong [HomeDefaults.colors]. */
    data class Solid(
        val argb: Int,
    ) : HomeBackground

    /** Ảnh trong `filesDir/home/[fileName]`; [dim] 0f..0.8f, mặc định 0.3f. */
    data class Image(
        val fileName: String,
        val dim: Float,
    ) : HomeBackground
}

/** Cấu hình trang chủ (spec 010). */
data class HomeConfig(
    val version: Int = 1,
    val shortcuts: List<Shortcut>,
    val background: HomeBackground = HomeBackground.Dynamic,
)

/** Giá trị mặc định của trang chủ (spec 010 AC7). */
object HomeDefaults {
    /** Shortcut khi chưa có cấu hình (chưa ghi xuống đĩa cho tới khi người dùng sửa). */
    val shortcuts: List<Shortcut> =
        listOf(
            Shortcut("default-wikipedia", "Wikipedia", "https://www.wikipedia.org/"),
            Shortcut("default-youtube", "YouTube", "https://www.youtube.com/"),
            Shortcut("default-github", "GitHub", "https://github.com/"),
            Shortcut("default-openstreetmap", "OpenStreetMap", "https://www.openstreetmap.org/"),
        )

    /** 8 màu nền dựng sẵn (ARGB, Material 600). */
    val colors: List<Int> =
        listOf(
            0xFF1E88E5, // blue
            0xFF00897B, // teal
            0xFF43A047, // green
            0xFFFDD835, // yellow
            0xFFFB8C00, // orange
            0xFFE53935, // red
            0xFF8E24AA, // purple
            0xFF546E7A, // blue grey
        ).map { it.toInt() }

    const val MAX_SHORTCUTS = 20

    /** Độ dài tối đa của title shortcut. */
    const val MAX_TITLE_LENGTH = 40

    /** Dim mặc định cho ảnh nền mới. */
    const val DEFAULT_DIM = 0.3f

    /** Dim tối đa của ảnh nền. */
    const val MAX_DIM = 0.8f
}

/** Kết quả thêm/sửa shortcut (spec 010 AC8–AC9). */
sealed interface ShortcutEdit {
    data class Ok(
        val config: HomeConfig,
    ) : ShortcutEdit

    data object Invalid : ShortcutEdit

    data object Duplicate : ShortcutEdit

    data object Full : ShortcutEdit
}

/** Phép biến đổi thuần trên [HomeConfig] (spec 010 AC8–AC9). */
object HomeConfigEditor {
    /** Thêm shortcut vào cuối; `input` chuẩn hóa như omnibox, phải là URL http/https. */
    fun add(
        c: HomeConfig,
        title: String,
        input: String,
        newId: () -> String,
    ): ShortcutEdit {
        val url = normalizeShortcutUrl(input) ?: return ShortcutEdit.Invalid
        if (c.shortcuts.any { it.url == url }) return ShortcutEdit.Duplicate
        if (c.shortcuts.size >= HomeDefaults.MAX_SHORTCUTS) return ShortcutEdit.Full
        val shortcut = Shortcut(newId(), shortcutTitle(title, url), url)
        return ShortcutEdit.Ok(c.copy(shortcuts = c.shortcuts + shortcut))
    }

    /** Sửa title/url của shortcut [id], giữ id và vị trí. Id lạ hoặc URL không hợp lệ → [ShortcutEdit.Invalid]. */
    fun update(
        c: HomeConfig,
        id: String,
        title: String,
        input: String,
    ): ShortcutEdit {
        val index = c.shortcuts.indexOfFirst { it.id == id }
        if (index < 0) return ShortcutEdit.Invalid
        val url = normalizeShortcutUrl(input) ?: return ShortcutEdit.Invalid
        if (c.shortcuts.any { it.id != id && it.url == url }) return ShortcutEdit.Duplicate
        val updated = c.shortcuts.toMutableList()
        updated[index] = Shortcut(id, shortcutTitle(title, url), url)
        return ShortcutEdit.Ok(c.copy(shortcuts = updated))
    }

    fun remove(
        c: HomeConfig,
        id: String,
    ): HomeConfig =
        if (c.shortcuts.none { it.id == id }) c else c.copy(shortcuts = c.shortcuts.filterNot { it.id == id })

    /** Chuyển shortcut ở [from] tới vị trí [to]; index ngoài biên được clamp. */
    fun move(
        c: HomeConfig,
        from: Int,
        to: Int,
    ): HomeConfig {
        if (c.shortcuts.isEmpty()) return c
        val last = c.shortcuts.lastIndex
        val f = from.coerceIn(0, last)
        val t = to.coerceIn(0, last)
        if (f == t) return c
        val list = c.shortcuts.toMutableList()
        list.add(t, list.removeAt(f))
        return c.copy(shortcuts = list)
    }

    /** Đặt nền; dim của ảnh được clamp về 0..[HomeDefaults.MAX_DIM]. */
    fun setBackground(
        c: HomeConfig,
        bg: HomeBackground,
    ): HomeConfig {
        val clamped =
            if (bg is HomeBackground.Image) bg.copy(dim = bg.dim.coerceIn(0f, HomeDefaults.MAX_DIM)) else bg
        return c.copy(background = clamped)
    }

    private fun normalizeShortcutUrl(input: String): String? {
        val result = OmniboxClassifier.classify(input, SearchEngines.Default) as? OmniboxResult.Navigate ?: return null
        return result.url.takeIf(::isWebUrl)
    }

    private fun shortcutTitle(
        title: String,
        url: String,
    ): String = title.trim().ifEmpty { UrlDisplay.host(url) }.take(HomeDefaults.MAX_TITLE_LENGTH)
}

/** URL http/https có host (cùng quy tắc với `isValidWebUrl` của `:core:engine`). */
internal fun isWebUrl(url: String): Boolean {
    if (url.any { it.isWhitespace() }) return false
    val scheme = url.substringBefore("://", "").lowercase()
    val rest = url.substringAfter("://", "")
    return (scheme == "http" || scheme == "https") && rest.isNotEmpty() && !rest.startsWith("/")
}
