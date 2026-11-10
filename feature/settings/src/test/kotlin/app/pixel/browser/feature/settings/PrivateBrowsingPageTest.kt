package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.pixel.browser.core.data.privacy.PrivacySettings
import app.pixel.browser.core.data.privacy.PrivateAutoClose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 016 AC22 – trang cài đặt "Duyệt ẩn danh". */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PrivateBrowsingPageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val changes = mutableListOf<PrivacySettings>()

    private val radios =
        mapOf(
            PrivateAutoClose.Never to SettingsTestTags.privateAutoClose("never"),
            PrivateAutoClose.Minutes5 to SettingsTestTags.privateAutoClose("5"),
            PrivateAutoClose.Minutes30 to SettingsTestTags.privateAutoClose("30"),
        )

    private fun show(
        settings: PrivacySettings = PrivacySettings(),
        available: Boolean = true,
    ) {
        rule.setContent {
            MaterialTheme { PrivateBrowsingPage(settings, privateAvailable = available, onChange = { changes += it }) }
        }
        rule.waitForIdle()
    }

    private fun assertOnlySelected(value: PrivateAutoClose) {
        radios.forEach { (v, tag) ->
            val node = rule.onNodeWithTag(tag)
            if (v == value) node.assertIsSelected() else node.assertIsNotSelected()
        }
    }

    // --- chặn chụp màn hình ---

    @Test
    fun ac22_screenshotsSwitch_isOn_byDefault() {
        show()
        rule.onNodeWithTag(SettingsTestTags.PRIVATE_SCREENSHOTS).assertIsOn()
    }

    @Test
    fun ac22_screenshotsSwitch_isOff_whenSettingDisabled() {
        show(PrivacySettings(blockPrivateScreenshots = false))
        rule.onNodeWithTag(SettingsTestTags.PRIVATE_SCREENSHOTS).assertIsOff()
    }

    @Test
    fun ac22_screenshotsSwitch_tap_callsOnChangeOnce_withToggledValue() {
        show(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5))
        rule.onNodeWithTag(SettingsTestTags.PRIVATE_SCREENSHOTS).performClick()
        rule.waitForIdle()
        assertEquals(listOf(PrivacySettings(false, PrivateAutoClose.Minutes5)), changes)
    }

    // --- tự đóng ---

    @Test
    fun ac22_autoClose_minutes5Selected_whenSettingSaysFive() {
        show(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5))
        assertOnlySelected(PrivateAutoClose.Minutes5)
    }

    @Test
    fun ac22_autoClose_minutes30Selected_whenSettingSaysThirty() {
        show(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes30))
        assertOnlySelected(PrivateAutoClose.Minutes30)
    }

    @Test
    fun ac22_autoClose_defaultIsNever() {
        show()
        assertOnlySelected(PrivateAutoClose.Never)
    }

    @Test
    fun ac22_autoClose_tapOtherOption_callsOnChangeOnce() {
        show()
        rule.onNodeWithTag(SettingsTestTags.privateAutoClose("30")).performClick()
        rule.waitForIdle()
        assertEquals(listOf(PrivacySettings(true, PrivateAutoClose.Minutes30)), changes)
    }

    @Test
    fun ac22_autoClose_tapSelectedOption_doesNotCallOnChange() {
        show(PrivacySettings(privateAutoClose = PrivateAutoClose.Minutes5))
        rule.onNodeWithTag(SettingsTestTags.privateAutoClose("5")).performClick()
        rule.waitForIdle()
        assertTrue(changes.isEmpty())
    }

    // --- bảng bảo đảm ---

    @Test
    fun ac22_guaranteeTable_isShown_withSubstantialContent() {
        show()
        val node = rule.onNodeWithTag(SettingsTestTags.PRIVATE_GUARANTEE)
        node.performScrollTo().assertExists()
        assertTrue("guarantee table must list both columns", node.textValue().length > 100)
    }

    // --- không khả dụng ---

    @Test
    fun ac22_unavailableNotice_isShown_whenPrivateIsUnavailable() {
        show(available = false)
        rule.onNodeWithTag(SettingsTestTags.PRIVATE_UNAVAILABLE).performScrollTo().assertExists()
    }

    @Test
    fun ac22_unavailableNotice_isAbsent_whenPrivateIsAvailable() {
        show(available = true)
        rule.onNodeWithTag(SettingsTestTags.PRIVATE_UNAVAILABLE).assertDoesNotExist()
    }
}
