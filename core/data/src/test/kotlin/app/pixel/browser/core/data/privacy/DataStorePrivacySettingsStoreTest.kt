package app.pixel.browser.core.data.privacy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pixel.browser.core.data.DataStoreThemeSettingsStore
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.data.createSettingsDataStore
import app.pixel.browser.core.data.search.CustomSearchEngine
import app.pixel.browser.core.data.search.DataStoreSearchSettingsStore
import app.pixel.browser.core.data.search.SearchSettings
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

/** Spec 016 AC23 – [DataStorePrivacySettingsStore] trên file tạm, chung file `settings` với theme/search. */
class DataStorePrivacySettingsStoreTest : PrivacySettingsStoreContractTest() {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private val file: File get() = File(tmp.root, "settings.preferences_pb")

    private val secureKey = booleanPreferencesKey("privacy_private_secure")
    private val autoCloseKey = intPreferencesKey("privacy_private_auto_close")

    private fun TestScope.newScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).also { scopes += it }

    private fun TestScope.rawDataStore(scope: CoroutineScope = newScope()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.close(scope: CoroutineScope) {
        scope.cancel()
        scopes -= scope
        advanceUntilIdle()
    }

    override fun TestScope.newStore(): PrivacySettingsStore =
        DataStorePrivacySettingsStore(createSettingsDataStore(newScope()) { file })

    override fun TestScope.cleanup() {
        scopes.toList().forEach { close(it) }
    }

    @Test
    fun ac23_newStoreOnSameFile_readsBackPersistedValues() =
        runTest {
            val first = newScope()
            DataStorePrivacySettingsStore(createSettingsDataStore(first) { file })
                .update {
                    PrivacySettings(
                        blockPrivateScreenshots = false,
                        privateAutoClose = PrivateAutoClose.Minutes30,
                    )
                }
            close(first)

            val reopened = DataStorePrivacySettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(PrivacySettings(false, PrivateAutoClose.Minutes30), reopened.settings.first())
            cleanup()
        }

    @Test
    fun ac23_update_writesSpecKeys_withMinutesAsInt() =
        runTest {
            val dataStore = createSettingsDataStore(newScope()) { file }
            DataStorePrivacySettingsStore(dataStore)
                .update {
                    PrivacySettings(
                        blockPrivateScreenshots = false,
                        privateAutoClose = PrivateAutoClose.Minutes5,
                    )
                }

            val prefs = dataStore.data.first()
            assertEquals(false, prefs[secureKey])
            assertEquals(5, prefs[autoCloseKey])
            cleanup()
        }

    @Test
    fun ac23_unknownAutoCloseValues_mapToNever() =
        runTest {
            listOf(7, -1, 1, 31, Int.MAX_VALUE).forEach { odd ->
                val dataStore = rawDataStore()
                dataStore.edit { it[autoCloseKey] = odd }
                assertEquals(
                    "value $odd",
                    PrivateAutoClose.Never,
                    DataStorePrivacySettingsStore(dataStore).settings.first().privateAutoClose,
                )
                // Mỗi vòng cần DataStore mới trên cùng file: hủy scope trước khi tạo cái kế tiếp.
                cleanup()
            }
        }

    @Test
    fun ac23_missingKeys_meanDefaults_screenshotsBlocked() =
        runTest {
            val dataStore = rawDataStore()
            dataStore.edit { it[stringPreferencesKey("theme_mode")] = "Dark" }
            assertEquals(PrivacySettings(), DataStorePrivacySettingsStore(dataStore).settings.first())
            cleanup()
        }

    @Test
    fun ac23_corruptedFile_isReplacedWithDefaults_withoutThrowing() =
        runTest {
            file.writeBytes(ByteArray(32) { 0xFF.toByte() })
            val store = DataStorePrivacySettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(PrivacySettings(), store.settings.first())
            store.update { it.copy(privateAutoClose = PrivateAutoClose.Minutes5) }
            assertEquals(PrivateAutoClose.Minutes5, store.settings.first().privateAutoClose)
            cleanup()
        }

    @Test
    fun ac23_interleavedWithThemeAndSearchStores_keysDoNotClobberEachOther() =
        runTest {
            val dataStore = createSettingsDataStore(newScope()) { file }
            val theme = DataStoreThemeSettingsStore(dataStore)
            val search = DataStoreSearchSettingsStore(dataStore)
            val privacy = DataStorePrivacySettingsStore(dataStore)
            val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")

            theme.update { ThemeSettings(ThemeMode.Dark, dynamicColor = false) }
            search.update { SearchSettings("bing", kagi) }
            privacy.update { PrivacySettings(false, PrivateAutoClose.Minutes30) }

            theme.update { it.copy(mode = ThemeMode.Light) }
            search.update { it.copy(selectedId = "duckduckgo") }

            assertEquals(PrivacySettings(false, PrivateAutoClose.Minutes30), privacy.settings.first())
            assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = false), theme.settings.first())
            assertEquals(SearchSettings("duckduckgo", kagi), search.settings.first())

            privacy.update { it.copy(blockPrivateScreenshots = true) }
            assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = false), theme.settings.first())
            assertEquals(SearchSettings("duckduckgo", kagi), search.settings.first())
            cleanup()
        }
}
