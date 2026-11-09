package app.pixel.browser.core.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/**
 * [SearchSettingsStore] trên DataStore Preferences (cùng file `settings` với theme, spec 014): các khóa
 * `search_engine_id`, `search_custom_name`, `search_custom_template`. Chỉ ghi khóa của mình.
 */
class DataStoreSearchSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : SearchSettingsStore {
    /** Retry `IOException` tối đa [MAX_READ_RETRIES] lần như store theme; vẫn lỗi thì phát mặc định nếu chưa nạp gì. */
    override val settings: Flow<SearchSettings> =
        flow {
            var loaded = false
            dataStore.data
                .map { it.toSearchSettings() }
                .retryWhen { cause, attempt ->
                    (cause is IOException && attempt < MAX_READ_RETRIES).also { retry ->
                        if (retry) delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }.catch { e ->
                    if (e !is IOException) throw e
                    if (!loaded) emit(SearchSettings())
                }.collect {
                    loaded = true
                    emit(it)
                }
        }.distinctUntilChanged()

    override suspend fun update(transform: (SearchSettings) -> SearchSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSearchSettings())
            prefs[ENGINE_ID] = next.selectedId
            val custom = next.custom
            if (custom == null) {
                prefs.remove(CUSTOM_NAME)
                prefs.remove(CUSTOM_TEMPLATE)
            } else {
                prefs[CUSTOM_NAME] = custom.name
                prefs[CUSTOM_TEMPLATE] = custom.template
            }
        }
    }

    private companion object {
        val ENGINE_ID = stringPreferencesKey("search_engine_id")
        val CUSTOM_NAME = stringPreferencesKey("search_custom_name")
        val CUSTOM_TEMPLATE = stringPreferencesKey("search_custom_template")
        const val MAX_READ_RETRIES = 3
        const val RETRY_DELAY_MS = 100L

        fun Preferences.toSearchSettings(): SearchSettings {
            val name = this[CUSTOM_NAME]
            val template = this[CUSTOM_TEMPLATE]
            return SearchSettings(
                selectedId = this[ENGINE_ID] ?: SearchSettings().selectedId,
                custom = if (name != null && template != null) CustomSearchEngine(name, template) else null,
            )
        }
    }
}
