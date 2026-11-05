package app.pixel.browser.core.omnibox

/** Kết quả phân loại input của omnibox (spec 004). */
sealed interface OmniboxResult {
    data class Navigate(
        val url: String,
    ) : OmniboxResult

    data class Search(
        val query: String,
        val url: String,
    ) : OmniboxResult

    data class Blocked(
        val input: String,
        val reason: BlockReason,
    ) : OmniboxResult

    data object Empty : OmniboxResult
}

enum class BlockReason { DangerousScheme, UnsupportedScheme }
