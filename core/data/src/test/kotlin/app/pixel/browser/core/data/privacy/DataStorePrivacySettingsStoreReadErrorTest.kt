package app.pixel.browser.core.data.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** Spec 016 AC23 – lỗi đọc đĩa: thử lại tối đa 3 lần như store theme; không ghi đè giá trị đã nạp. */
class DataStorePrivacySettingsStoreReadErrorTest {
    private val m5Prefs: Preferences = preferencesOf(intPreferencesKey("privacy_private_auto_close") to 5)

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
                    emit(m5Prefs)
                }
            val values = DataStorePrivacySettingsStore(dataStore).settings.toList()
            assertEquals(listOf(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5)), values)
            assertEquals(3, dataStore.attempts)
        }

    @Test
    fun persistentReadFailure_retriesThreeTimes_thenEmitsDefaults() =
        runTest {
            val dataStore = ScriptedDataStore { throw IOException("disk error") }
            val values = DataStorePrivacySettingsStore(dataStore).settings.toList()
            assertEquals(listOf(PrivacySettings()), values)
            assertEquals(4, dataStore.attempts)
        }

    @Test
    fun failureAfterLoadedValue_doesNotOverwriteWithDefaults() =
        runTest {
            val dataStore =
                ScriptedDataStore {
                    emit(m5Prefs)
                    throw IOException("later error")
                }
            val values = DataStorePrivacySettingsStore(dataStore).settings.toList()
            assertEquals(listOf(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5)), values)
        }

    @Test(expected = IllegalStateException::class)
    fun nonIoFailure_propagates() =
        runTest {
            DataStorePrivacySettingsStore(ScriptedDataStore { error("bug") }).settings.toList()
        }
}
