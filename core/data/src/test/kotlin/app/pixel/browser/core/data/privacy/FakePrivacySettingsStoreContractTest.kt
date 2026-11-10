package app.pixel.browser.core.data.privacy

import kotlinx.coroutines.test.TestScope

/** Spec 016 AC23 – Fake chạy cùng bộ hợp đồng với DataStore. */
class FakePrivacySettingsStoreContractTest : PrivacySettingsStoreContractTest() {
    override fun TestScope.newStore(): PrivacySettingsStore = FakePrivacySettingsStore()
}
