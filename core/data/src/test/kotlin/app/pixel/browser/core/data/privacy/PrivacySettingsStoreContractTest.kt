package app.pixel.browser.core.data.privacy

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 016 AC23 – hợp đồng chung của [PrivacySettingsStore] (Fake và DataStore). */
abstract class PrivacySettingsStoreContractTest {
    protected abstract fun TestScope.newStore(): PrivacySettingsStore

    protected open fun TestScope.cleanup() {}

    private fun storeTest(block: suspend TestScope.(PrivacySettingsStore) -> Unit) =
        runTest {
            val store = newStore()
            try {
                block(store)
            } finally {
                cleanup()
            }
        }

    @Test
    fun ac23_newStore_emitsDefaults() =
        storeTest { store ->
            val defaults = store.settings.first()
            assertEquals(PrivacySettings(), defaults)
            assertTrue(defaults.blockPrivateScreenshots)
            assertEquals(PrivateAutoClose.Never, defaults.privateAutoClose)
        }

    @Test
    fun ac23_update_emitsNewValue() =
        storeTest { store ->
            store.update { it.copy(blockPrivateScreenshots = false) }
            assertEquals(PrivacySettings(blockPrivateScreenshots = false), store.settings.first())
        }

    @Test
    fun ac23_everyAutoCloseValue_roundTrips() =
        storeTest { store ->
            PrivateAutoClose.entries.forEach { value ->
                store.update { it.copy(privateAutoClose = value) }
                assertEquals(value, store.settings.first().privateAutoClose)
            }
        }

    @Test
    fun ac23_updates_areSequential_transformSeesPreviousValue() =
        storeTest { store ->
            store.update { it.copy(privateAutoClose = PrivateAutoClose.Minutes30) }
            store.update { it.copy(blockPrivateScreenshots = false) }
            assertEquals(PrivacySettings(false, PrivateAutoClose.Minutes30), store.settings.first())
        }

    @Test
    fun ac23_reEmitsOnChange_withoutDuplicates() =
        storeTest { store ->
            val collected = mutableListOf<PrivacySettings>()
            val job = launch { store.settings.collect { collected += it } }
            advanceUntilIdle()
            store.update { it.copy(privateAutoClose = PrivateAutoClose.Minutes5) }
            advanceUntilIdle()
            store.update { it.copy(blockPrivateScreenshots = false) }
            advanceUntilIdle()
            job.cancel()
            assertEquals(
                listOf(
                    PrivacySettings(),
                    PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5),
                    PrivacySettings(blockPrivateScreenshots = false, privateAutoClose = PrivateAutoClose.Minutes5),
                ),
                collected,
            )
        }
}
