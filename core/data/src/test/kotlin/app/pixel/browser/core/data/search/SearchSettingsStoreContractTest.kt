package app.pixel.browser.core.data.search

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 014 AC4 – hợp đồng chung của [SearchSettingsStore] (Fake và DataStore). */
abstract class SearchSettingsStoreContractTest {
    protected abstract fun TestScope.newStore(): SearchSettingsStore

    protected open fun TestScope.cleanup() {}

    private fun storeTest(block: suspend TestScope.(SearchSettingsStore) -> Unit) =
        runTest {
            val store = newStore()
            try {
                block(store)
            } finally {
                cleanup()
            }
        }

    private val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")

    @Test
    fun ac4_newStore_emitsDefaults() =
        storeTest { store ->
            assertEquals(SearchSettings(), store.settings.first())
        }

    @Test
    fun ac4_update_emitsNewValue() =
        storeTest { store ->
            store.update { it.copy(selectedId = "duckduckgo") }
            assertEquals(SearchSettings(selectedId = "duckduckgo"), store.settings.first())
        }

    @Test
    fun ac4_updates_areSequential_transformSeesPreviousValue() =
        storeTest { store ->
            store.update { it.copy(selectedId = "brave") }
            store.update { it.copy(custom = kagi) }
            assertEquals(SearchSettings("brave", kagi), store.settings.first())
        }

    @Test
    fun ac4_customIsKept_whenSwitchingToBuiltInAndBack() =
        storeTest { store ->
            store.update { SearchSettings(SearchEngineIds.CUSTOM, kagi) }
            store.update { it.copy(selectedId = "bing") }
            assertEquals(SearchSettings("bing", kagi), store.settings.first())
            store.update { it.copy(selectedId = SearchEngineIds.CUSTOM) }
            assertEquals(SearchSettings(SearchEngineIds.CUSTOM, kagi), store.settings.first())
        }

    @Test
    fun ac4_reEmitsOnChange() =
        storeTest { store ->
            val collected = mutableListOf<SearchSettings>()
            val job = launch { store.settings.collect { collected += it } }
            advanceUntilIdle()
            store.update { it.copy(selectedId = "bing") }
            advanceUntilIdle()
            store.update { it.copy(selectedId = "brave") }
            advanceUntilIdle()
            job.cancel()
            assertEquals(
                listOf(SearchSettings(), SearchSettings(selectedId = "bing"), SearchSettings(selectedId = "brave")),
                collected,
            )
        }
}
