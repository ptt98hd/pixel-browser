package app.pixel.browser.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Spec 011 AC5 – [DataStoreThemeSettingsStore] trên file tạm. */
class DataStoreThemeSettingsStoreTest : ThemeSettingsStoreContractTest() {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private val file: File get() = File(tmp.root, "settings.preferences_pb")

    /** Mỗi DataStore một scope riêng để có thể đóng rồi mở lại cùng file (DataStore chỉ cho 1 instance/file). */
    private fun TestScope.newScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).also { scopes += it }

    /** DataStore "thô" không có corruption handler (để ghi giá trị lạ trực tiếp). */
    private fun TestScope.rawDataStore(scope: CoroutineScope = newScope()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.close(scope: CoroutineScope) {
        scope.cancel()
        scopes -= scope
        advanceUntilIdle()
    }

    override fun TestScope.newStore(): ThemeSettingsStore =
        DataStoreThemeSettingsStore(
            createSettingsDataStore(newScope()) {
                file
            },
        )

    override fun TestScope.cleanup() {
        scopes.toList().forEach { close(it) }
    }

    @Test
    fun ac5_newStoreOnSameFile_readsBackPersistedMode() =
        runTest {
            val first = newScope()
            DataStoreThemeSettingsStore(
                createSettingsDataStore(first) { file },
            ).update { it.copy(mode = ThemeMode.Dark) }
            close(first)

            val reopened = DataStoreThemeSettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(ThemeSettings(ThemeMode.Dark, dynamicColor = true), reopened.settings.first())
            cleanup()
        }

    @Test
    fun ac5_update_writesSpecKeys_themeModeStringAndDynamicColorBoolean() =
        runTest {
            val scope = newScope()
            val dataStore = createSettingsDataStore(scope) { file }
            DataStoreThemeSettingsStore(dataStore).update { ThemeSettings(ThemeMode.Light, dynamicColor = false) }

            val prefs = dataStore.data.first()
            assertEquals("Light", prefs[stringPreferencesKey("theme_mode")])
            assertEquals(false, prefs[booleanPreferencesKey("dynamic_color")])
            cleanup()
        }

    @Test
    fun ac5_unknownThemeModeValue_fallsBackToSystem() =
        runTest {
            val dataStore = rawDataStore()
            dataStore.edit {
                it[stringPreferencesKey("theme_mode")] = "Purple"
                it[booleanPreferencesKey("dynamic_color")] = false
            }

            val settings = DataStoreThemeSettingsStore(dataStore).settings.first()
            assertEquals(ThemeSettings(ThemeMode.System, dynamicColor = false), settings)
            cleanup()
        }

    @Test
    fun ac5_corruptedFile_isReplacedWithDefaults_withoutThrowing() =
        runTest {
            file.writeBytes(ByteArray(32) { 0xFF.toByte() })

            val store = DataStoreThemeSettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(ThemeSettings(), store.settings.first())

            store.update { it.copy(mode = ThemeMode.Dark) }
            assertEquals(ThemeMode.Dark, store.settings.first().mode)
            cleanup()
        }
}
