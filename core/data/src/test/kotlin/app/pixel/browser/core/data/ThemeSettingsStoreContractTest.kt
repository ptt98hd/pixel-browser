package app.pixel.browser.core.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 011 AC5 – hợp đồng chung của [ThemeSettingsStore] (Fake và DataStore). */
abstract class ThemeSettingsStoreContractTest {
    /** Tạo store mới trên trạng thái sạch (chưa có giá trị lưu). */
    protected abstract fun TestScope.newStore(): ThemeSettingsStore

    /** Gọi sau mỗi test để đóng tài nguyên (scope DataStore…). */
    protected open fun TestScope.cleanup() {}

    private fun storeTest(block: suspend TestScope.(ThemeSettingsStore) -> Unit) =
        runTest {
            val store = newStore()
            try {
                block(store)
            } finally {
                cleanup()
            }
        }

    @Test
    fun ac5_newStore_emitsDefaults_systemAndDynamicColor() =
        storeTest { store ->
            assertEquals(ThemeSettings(ThemeMode.System, dynamicColor = true), store.settings.first())
        }

    @Test
    fun ac5_updateMode_emitsUpdatedSettings() =
        storeTest { store ->
            store.update { it.copy(mode = ThemeMode.Dark) }
            assertEquals(ThemeSettings(ThemeMode.Dark, dynamicColor = true), store.settings.first())
        }

    @Test
    fun ac5_update_transformReceivesCurrentValue() =
        storeTest { store ->
            store.update { it.copy(mode = ThemeMode.Light) }
            store.update { it.copy(dynamicColor = false) }
            assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = false), store.settings.first())
        }
}
