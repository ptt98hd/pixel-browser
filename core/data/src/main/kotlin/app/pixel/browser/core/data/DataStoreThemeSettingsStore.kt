package app.pixel.browser.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.File
import java.io.IOException

/**
 * [ThemeSettingsStore] trên DataStore Preferences: khóa `"theme_mode"` (String, tên [ThemeMode]; giá trị lạ →
 * [ThemeMode.System]) và `"dynamic_color"` (Boolean).
 */
class DataStoreThemeSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : ThemeSettingsStore {
    /**
     * Lỗi đọc đĩa (`IOException`) được thử lại tối đa [MAX_READ_RETRIES] lần; vẫn lỗi thì phát mặc định nếu
     * chưa nạp được giá trị nào, còn đã nạp rồi thì giữ giá trị đó (không phát mặc định đè lên). Lỗi khác ném ra.
     * File hỏng do corruption handler của [createSettingsDataStore] xử lý.
     */
    override val settings: Flow<ThemeSettings> =
        flow {
            var loaded = false
            dataStore.data
                .map { it.toThemeSettings() }
                .retryWhen { cause, attempt ->
                    (cause is IOException && attempt < MAX_READ_RETRIES).also { retry ->
                        if (retry) delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }.catch { e ->
                    if (e !is IOException) throw e
                    if (!loaded) emit(DEFAULTS)
                }.collect {
                    loaded = true
                    emit(it)
                }
        }.distinctUntilChanged()

    override suspend fun update(transform: (ThemeSettings) -> ThemeSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toThemeSettings())
            prefs[THEME_MODE] = next.mode.name
            prefs[DYNAMIC_COLOR] = next.dynamicColor
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULTS = ThemeSettings()
        const val MAX_READ_RETRIES = 3
        const val RETRY_DELAY_MS = 100L

        fun Preferences.toThemeSettings(): ThemeSettings =
            ThemeSettings(
                mode = ThemeMode.entries.firstOrNull { it.name == this[THEME_MODE] } ?: DEFAULTS.mode,
                dynamicColor = this[DYNAMIC_COLOR] ?: DEFAULTS.dynamicColor,
            )
    }
}

/**
 * Tạo DataStore cho file cài đặt chung (`settings.preferences_pb`) với `ReplaceFileCorruptionHandler` thay file
 * hỏng bằng mặc định (spec 011 AC5). [produceFile] phải trả file có đuôi `.preferences_pb`.
 *
 * DataStore chỉ cho phép một instance đang hoạt động mỗi file trong process: gọi một lần cho mỗi file và giữ lại.
 */
fun createSettingsDataStore(
    scope: CoroutineScope,
    produceFile: () -> File,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )
