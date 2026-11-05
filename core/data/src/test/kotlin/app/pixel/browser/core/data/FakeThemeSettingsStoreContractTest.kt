package app.pixel.browser.core.data

import kotlinx.coroutines.test.TestScope

class FakeThemeSettingsStoreContractTest : ThemeSettingsStoreContractTest() {
    override fun TestScope.newStore(): ThemeSettingsStore = FakeThemeSettingsStore()
}
