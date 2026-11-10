package app.pixel.browser.core.data.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException

/**
 * [PrivacySettingsStore] trên DataStore Preferences (file `settings` chung): khóa `privacy_private_secure`
 * (Boolean) và `privacy_private_auto_close` (Int phút; giá trị lạ → [PrivateAutoClose.Never]). Chỉ ghi khóa của mình.
 */
class DataStorePrivacySettingsStore(
    private val dataStore: DataStore<Preferences>,
) : PrivacySettingsStore {
    /** Retry `IOException` tối đa [MAX_READ_RETRIES] lần như store theme; vẫn lỗi thì phát mặc định nếu chưa nạp gì. */
    override val settings: Flow<PrivacySettings> =
        flow {
            var loaded = false
            dataStore.data
                .map { it.toPrivacySettings() }
                .retryWhen { cause, attempt ->
                    (cause is IOException && attempt < MAX_READ_RETRIES).also { retry ->
                        if (retry) delay(RETRY_DELAY_MS * (attempt + 1))
                    }
                }.catch { e ->
                    if (e !is IOException) throw e
                    if (!loaded) emit(PrivacySettings())
                }.collect {
                    loaded = true
                    emit(it)
                }
        }.distinctUntilChanged()

    override suspend fun update(transform: (PrivacySettings) -> PrivacySettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toPrivacySettings())
            prefs[PRIVATE_SECURE] = next.blockPrivateScreenshots
            prefs[PRIVATE_AUTO_CLOSE] = next.privateAutoClose.minutes
        }
    }

    private companion object {
        val PRIVATE_SECURE = booleanPreferencesKey("privacy_private_secure")
        val PRIVATE_AUTO_CLOSE = intPreferencesKey("privacy_private_auto_close")
        const val MAX_READ_RETRIES = 3
        const val RETRY_DELAY_MS = 100L

        fun Preferences.toPrivacySettings(): PrivacySettings {
            val defaults = PrivacySettings()
            return PrivacySettings(
                blockPrivateScreenshots = this[PRIVATE_SECURE] ?: defaults.blockPrivateScreenshots,
                privateAutoClose =
                    PrivateAutoClose.entries.firstOrNull { it.minutes == this[PRIVATE_AUTO_CLOSE] }
                        ?: PrivateAutoClose.Never,
            )
        }
    }
}
