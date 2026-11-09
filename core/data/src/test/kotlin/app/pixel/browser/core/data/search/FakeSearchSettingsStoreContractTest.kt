package app.pixel.browser.core.data.search

import kotlinx.coroutines.test.TestScope

/** Spec 014 AC4 – Fake chạy cùng bộ hợp đồng với DataStore. */
class FakeSearchSettingsStoreContractTest : SearchSettingsStoreContractTest() {
    override fun TestScope.newStore(): SearchSettingsStore = FakeSearchSettingsStore()
}
