package app.pixel.browser.core.data.search

import app.pixel.browser.core.omnibox.SearchEngine

sealed interface CustomEngineResult {
    data class Ok(
        val engine: CustomSearchEngine,
    ) : CustomEngineResult

    data object NameEmpty : CustomEngineResult

    data object NameTooLong : CustomEngineResult

    data object TemplateEmpty : CustomEngineResult

    data object NoPlaceholder : CustomEngineResult

    data object MultiplePlaceholders : CustomEngineResult

    data object InvalidUrl : CustomEngineResult

    data object TemplateTooLong : CustomEngineResult
}

object CustomEngineValidator {
    const val MAX_NAME = 40
    const val MAX_TEMPLATE = 2048

    private const val PLACEHOLDER = "%s"
    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

    /** Kiểm tra thuần cục bộ, không gọi mạng. Kết quả [CustomEngineResult.Ok] đã chuẩn hóa (trim, thêm `https://`). */
    fun validate(
        name: String,
        template: String,
    ): CustomEngineResult {
        val cleanName = name.filterNot(::isInvisible).trimSpaces()
        val cleanTemplate = template.trimSpaces()
        return when {
            cleanName.isEmpty() -> CustomEngineResult.NameEmpty
            cleanName.length > MAX_NAME -> CustomEngineResult.NameTooLong
            cleanTemplate.isEmpty() -> CustomEngineResult.TemplateEmpty
            cleanTemplate.length > MAX_TEMPLATE -> CustomEngineResult.TemplateTooLong
            else -> validateTemplate(cleanName, cleanTemplate)
        }
    }

    private fun validateTemplate(
        name: String,
        template: String,
    ): CustomEngineResult {
        val normalized = if (SCHEME.containsMatchIn(template)) template else "https://$template"
        val placeholders = normalized.windowed(PLACEHOLDER.length).count { it == PLACEHOLDER }
        return when {
            normalized.any {
                it.isWhitespace() ||
                    Character.isSpaceChar(
                        it,
                    ) || it.isISOControl()
            } -> CustomEngineResult.InvalidUrl

            placeholders == 0 -> CustomEngineResult.NoPlaceholder

            placeholders > 1 -> CustomEngineResult.MultiplePlaceholders

            !isBuildable(normalized) -> CustomEngineResult.InvalidUrl

            else -> CustomEngineResult.Ok(CustomSearchEngine(name, normalized))
        }
    }

    /** Ký tự điều khiển và bidi override/isolate/mark: không được lọt vào tên hiển thị. */
    private fun isInvisible(c: Char): Boolean =
        c.isISOControl() || c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069' || c == '\u200E' || c == '\u200F'

    private fun String.trimSpaces(): String = trim { it.isWhitespace() || Character.isSpaceChar(it) }

    private fun isBuildable(template: String): Boolean =
        try {
            SearchEngine(SearchEngineIds.CUSTOM, "custom", template)
            true
        } catch (_: IllegalArgumentException) {
            false
        }

    /** Template dùng scheme `http` (truy vấn đi không mã hóa). */
    fun isInsecure(template: String): Boolean = template.trimSpaces().startsWith("http://", ignoreCase = true)
}
