package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 014 AC15 – trang Giao diện (cùng hành vi với ThemeDialog, spec 011 AC11). */
@RunWith(RobolectricTestRunner::class)
class AppearancePageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val changes = mutableListOf<ThemeSettings>()

    private val tags =
        mapOf(
            ThemeMode.System to SettingsTestTags.appearanceMode("system"),
            ThemeMode.Light to SettingsTestTags.appearanceMode("light"),
            ThemeMode.Dark to SettingsTestTags.appearanceMode("dark"),
        )

    private fun show(settings: ThemeSettings) {
        rule.setContent { MaterialTheme { AppearancePage(settings, onChange = { changes += it }) } }
        rule.waitForIdle()
    }

    private fun assertOnlySelected(mode: ThemeMode) {
        tags.forEach { (m, tag) ->
            if (m == mode) rule.onNodeWithTag(tag).assertIsSelected() else rule.onNodeWithTag(tag).assertIsNotSelected()
        }
    }

    @Test
    fun ac15_selectedMode_followsSettings() {
        show(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        assertOnlySelected(ThemeMode.Light)
    }

    @Test
    fun ac15_darkSelected_whenSettingsSayDark() {
        show(ThemeSettings(ThemeMode.Dark, dynamicColor = false))
        assertOnlySelected(ThemeMode.Dark)
    }

    @Test
    fun ac15_modes_areRadioButtons() {
        show(ThemeSettings())
        tags.values.forEach {
            assertEquals(Role.RadioButton, rule.onNodeWithTag(it).fetchSemanticsNode().config[SemanticsProperties.Role])
        }
    }

    @Test
    fun ac15_dynamicSwitch_reflectsSetting() {
        show(ThemeSettings(dynamicColor = true))
        rule.onNodeWithTag(SettingsTestTags.APPEARANCE_DYNAMIC).assertIsOn()
    }

    @Test
    fun ac15_dynamicSwitch_offWhenDisabled() {
        show(ThemeSettings(dynamicColor = false))
        rule.onNodeWithTag(SettingsTestTags.APPEARANCE_DYNAMIC).assertIsOff()
    }

    @Test
    fun ac15_tapDark_callsOnChangeOnce_withDarkAndSameDynamicColor() {
        show(ThemeSettings(ThemeMode.System, dynamicColor = false))
        rule.onNodeWithTag(tags.getValue(ThemeMode.Dark)).performClick()
        assertEquals(listOf(ThemeSettings(ThemeMode.Dark, dynamicColor = false)), changes)
    }

    @Test
    fun ac15_toggleDynamic_callsOnChangeWithCopy() {
        show(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        rule.onNodeWithTag(SettingsTestTags.APPEARANCE_DYNAMIC).performClick()
        assertEquals(listOf(ThemeSettings(ThemeMode.Light, dynamicColor = false)), changes)
    }

    @Test
    fun ac15_toggleDynamicOn_callsOnChangeWithTrue() {
        show(ThemeSettings(ThemeMode.Dark, dynamicColor = false))
        rule.onNodeWithTag(SettingsTestTags.APPEARANCE_DYNAMIC).performClick()
        assertEquals(listOf(ThemeSettings(ThemeMode.Dark, dynamicColor = true)), changes)
    }

    @Test
    fun ac15_tapAlreadySelectedMode_doesNotCallOnChange() {
        show(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        rule.onNodeWithTag(tags.getValue(ThemeMode.Light)).performClick()
        assertTrue(changes.isEmpty())
    }
}
