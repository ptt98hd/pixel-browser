package app.pixel.browser.core.data.search

import app.pixel.browser.core.omnibox.SearchEngine
import app.pixel.browser.core.omnibox.SearchEngines
import kotlinx.coroutines.flow.Flow

/** Engine tùy chỉnh duy nhất do người dùng nhập (spec 014). */
data class CustomSearchEngine(
    val name: String,
    val template: String,
)

object SearchEngineIds {
    const val CUSTOM = "custom"
}

/** Cài đặt công cụ tìm kiếm được lưu (spec 014). */
data class SearchSettings(
    val selectedId: String = SearchEngines.Default.id,
    val custom: CustomSearchEngine? = null,
) {
    /** Engine hiệu lực: id dựng sẵn, hoặc custom hợp lệ, còn lại Default. */
    val engine: SearchEngine get() =
        if (selectedId == SearchEngineIds.CUSTOM) {
            val result = custom?.let { CustomEngineValidator.validate(it.name, it.template) }
            (result as? CustomEngineResult.Ok)
                ?.let { SearchEngine(SearchEngineIds.CUSTOM, it.engine.name, it.engine.template) }
                ?: SearchEngines.Default
        } else {
            SearchEngines.all.firstOrNull { it.id == selectedId } ?: SearchEngines.Default
        }
}

interface SearchSettingsStore {
    val settings: Flow<SearchSettings>

    suspend fun update(transform: (SearchSettings) -> SearchSettings)
}
