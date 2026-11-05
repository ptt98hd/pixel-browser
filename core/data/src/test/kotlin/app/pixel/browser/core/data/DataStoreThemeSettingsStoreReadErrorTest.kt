package app.pixel.browser.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Spec 011 AC5 (review) – lỗi đọc đĩa: thử lại, mặc định khi chưa nạp được, không ghi đè giá trị đã nạp. */
class DataStoreThemeSettingsStoreReadErrorTest {
    private val darkPrefs: Preferences = preferencesOf(stringPreferencesKey("theme_mode") to "Dark")

    /** DataStore chỉ đọc; mỗi lần collect `data` gọi [read] với số lần thử (bắt đầu 1). */
    private class ScriptedDataStore(
        private val read: suspend kotlinx.coroutines.flow.FlowCollector<Preferences>.(attempt: Int) -> Unit,
    ) : DataStore<Preferences> {
        var attempts = 0

        override val data: Flow<Preferences>
            get() = flow { read(++attempts) }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(emptyPreferences())
    }

    @Test
    fun transientReadFailure_isRetried_andEmitsStoredValue() =
        runTest {
            val dataStore =
                ScriptedDataStore { attempt ->
                    if (attempt <= 2) throw IOException("busy")
                    emit(darkPrefs)
                }
            val values = DataStoreThemeSettingsStore(dataStore).settings.toList()
            assertEquals(listOf(ThemeSettings(ThemeMode.Dark)), values)
            assertEquals(3, dataStore.attempts)
        }

    @Test
    fun persistentReadFailure_emitsDefaults_withoutThrowing() =
        runTest {
            val dataStore = ScriptedDataStore { throw IOException("disk error") }
            val values = DataStoreThemeSettingsStore(dataStore).settings.toList()
            assertEquals(listOf(ThemeSettings()), values)
        }

    @Test
    fun failureAfterLoadedValue_doesNotOverwriteWithDefaults() =
        runTest {
            val dataStore =
                ScriptedDataStore {
                    emit(darkPrefs)
                    throw IOException("later error")
                }
            val values = DataStoreThemeSettingsStore(dataStore).settings.toList()
            assertEquals(listOf(ThemeSettings(ThemeMode.Dark)), values)
        }

    @Test(expected = IllegalStateException::class)
    fun nonIoFailure_propagates() =
        runTest {
            DataStoreThemeSettingsStore(ScriptedDataStore { error("bug") }).settings.toList()
        }
}
