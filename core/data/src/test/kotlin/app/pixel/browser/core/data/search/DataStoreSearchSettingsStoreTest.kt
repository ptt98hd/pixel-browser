package app.pixel.browser.core.data.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pixel.browser.core.data.DataStoreThemeSettingsStore
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.data.createSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Spec 014 AC3 – [DataStoreSearchSettingsStore] trên file tạm, chung file với theme. */
class DataStoreSearchSettingsStoreTest : SearchSettingsStoreContractTest() {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    private val file: File get() = File(tmp.root, "settings.preferences_pb")

    private val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")

    private fun TestScope.newScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler) + Job()).also { scopes += it }

    private fun TestScope.rawDataStore(scope: CoroutineScope = newScope()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.close(scope: CoroutineScope) {
        scope.cancel()
        scopes -= scope
        advanceUntilIdle()
    }

    override fun TestScope.newStore(): SearchSettingsStore =
        DataStoreSearchSettingsStore(
            createSettingsDataStore(newScope()) {
                file
            },
        )

    override fun TestScope.cleanup() {
        scopes.toList().forEach { close(it) }
    }

    @Test
    fun ac3_newStoreOnSameFile_readsBackPersistedEngine() =
        runTest {
            val first = newScope()
            DataStoreSearchSettingsStore(createSettingsDataStore(first) { file })
                .update { it.copy(selectedId = "duckduckgo") }
            close(first)

            val reopened = DataStoreSearchSettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(SearchSettings(selectedId = "duckduckgo"), reopened.settings.first())
            cleanup()
        }

    @Test
    fun ac3_customEngine_roundTrips_includingVietnameseAndEmoji() =
        runTest {
            val custom = CustomSearchEngine("Tìm kiếm 🔎 Việt", "https://tìm.vn/search?q=%s&l=vi🙂")
            val first = newScope()
            DataStoreSearchSettingsStore(createSettingsDataStore(first) { file })
                .update { SearchSettings(SearchEngineIds.CUSTOM, custom) }
            close(first)

            val reopened = DataStoreSearchSettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(SearchSettings(SearchEngineIds.CUSTOM, custom), reopened.settings.first())
            cleanup()
        }

    @Test
    fun ac3_update_writesSpecKeys() =
        runTest {
            val dataStore = createSettingsDataStore(newScope()) { file }
            DataStoreSearchSettingsStore(dataStore).update { SearchSettings(SearchEngineIds.CUSTOM, kagi) }

            val prefs = dataStore.data.first()
            assertEquals("custom", prefs[stringPreferencesKey("search_engine_id")])
            assertEquals("Kagi", prefs[stringPreferencesKey("search_custom_name")])
            assertEquals(kagi.template, prefs[stringPreferencesKey("search_custom_template")])
            cleanup()
        }

    @Test
    fun ac3_onlyNameKey_meansNoCustom() =
        runTest {
            val dataStore = rawDataStore()
            dataStore.edit {
                it[stringPreferencesKey("search_engine_id")] = "custom"
                it[stringPreferencesKey("search_custom_name")] = "Kagi"
            }
            val settings = DataStoreSearchSettingsStore(dataStore).settings.first()
            assertNull(settings.custom)
            assertEquals("custom", settings.selectedId)
            cleanup()
        }

    @Test
    fun ac3_onlyTemplateKey_meansNoCustom() =
        runTest {
            val dataStore = rawDataStore()
            dataStore.edit { it[stringPreferencesKey("search_custom_template")] = kagi.template }
            assertNull(DataStoreSearchSettingsStore(dataStore).settings.first().custom)
            cleanup()
        }

    @Test
    fun ac3_corruptedFile_isReplacedWithDefaults_withoutThrowing() =
        runTest {
            file.writeBytes(ByteArray(32) { 0xFF.toByte() })

            val store = DataStoreSearchSettingsStore(createSettingsDataStore(newScope()) { file })
            assertEquals(SearchSettings(), store.settings.first())

            store.update { it.copy(selectedId = "brave") }
            assertEquals("brave", store.settings.first().selectedId)
            cleanup()
        }

    @Test
    fun ac3_searchUpdate_doesNotChangeThemeKeys_andViceVersa() =
        runTest {
            val dataStore = createSettingsDataStore(newScope()) { file }
            val theme = DataStoreThemeSettingsStore(dataStore)
            val search = DataStoreSearchSettingsStore(dataStore)

            theme.update { ThemeSettings(ThemeMode.Dark, dynamicColor = false) }
            search.update { SearchSettings("bing", kagi) }
            assertEquals(ThemeSettings(ThemeMode.Dark, dynamicColor = false), theme.settings.first())

            theme.update { it.copy(mode = ThemeMode.Light) }
            assertEquals(SearchSettings("bing", kagi), search.settings.first())

            search.update { it.copy(selectedId = "duckduckgo") }
            theme.update { it.copy(dynamicColor = true) }
            assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = true), theme.settings.first())
            assertEquals(SearchSettings("duckduckgo", kagi), search.settings.first())

            val prefs = dataStore.data.first()
            assertEquals("Light", prefs[stringPreferencesKey("theme_mode")])
            assertEquals(true, prefs[booleanPreferencesKey("dynamic_color")])
            cleanup()
        }
}
