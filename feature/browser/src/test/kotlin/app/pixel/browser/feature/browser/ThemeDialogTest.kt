package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_THEME
import app.pixel.browser.feature.browser.BrowserTestTags.THEME_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.THEME_DYNAMIC
import app.pixel.browser.feature.browser.BrowserTestTags.THEME_MODE_DARK
import app.pixel.browser.feature.browser.BrowserTestTags.THEME_MODE_LIGHT
import app.pixel.browser.feature.browser.BrowserTestTags.THEME_MODE_SYSTEM
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 011 AC11 – menu → ThemeDialog → `BrowserActions.onThemeChange`. */
@RunWith(RobolectricTestRunner::class)
class ThemeDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val changes = mutableListOf<ThemeSettings>()

    private val modeTags =
        mapOf(
            ThemeMode.System to THEME_MODE_SYSTEM,
            ThemeMode.Light to THEME_MODE_LIGHT,
            ThemeMode.Dark to THEME_MODE_DARK,
        )

    private fun showAndOpenDialog(settings: ThemeSettings) {
        rule.setContent {
            BrowserScreen(
                state = BrowserUiState(url = "https://example.com", tabCount = 1),
                actions = BrowserActions(onThemeChange = { changes += it }),
                themeSettings = settings,
            )
        }
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.onNodeWithTag(MENU_THEME).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(THEME_DIALOG).assertIsDisplayed()
    }

    private fun assertOnlySelected(mode: ThemeMode) {
        modeTags.forEach { (m, tag) ->
            if (m == mode) rule.onNodeWithTag(tag).assertIsSelected() else rule.onNodeWithTag(tag).assertIsNotSelected()
        }
    }

    @Test
    fun ac11_menuTheme_opensDialog_withSystemSelected() {
        showAndOpenDialog(ThemeSettings(ThemeMode.System, dynamicColor = true))
        assertOnlySelected(ThemeMode.System)
    }

    @Test
    fun ac11_dialog_marksLightSelected() {
        showAndOpenDialog(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        assertOnlySelected(ThemeMode.Light)
    }

    @Test
    fun ac11_dialog_marksDarkSelected() {
        showAndOpenDialog(ThemeSettings(ThemeMode.Dark, dynamicColor = true))
        assertOnlySelected(ThemeMode.Dark)
    }

    @Test
    fun ac11_dynamicSwitch_reflectsSetting() {
        showAndOpenDialog(ThemeSettings(ThemeMode.System, dynamicColor = true))
        rule.onNodeWithTag(THEME_DYNAMIC).assertIsOn()
    }

    @Test
    fun ac11_dynamicSwitch_offWhenDisabled() {
        showAndOpenDialog(ThemeSettings(ThemeMode.System, dynamicColor = false))
        rule.onNodeWithTag(THEME_DYNAMIC).assertIsOff()
    }

    @Test
    fun ac11_tapDark_callsOnThemeChangeOnce_andClosesDialog() {
        showAndOpenDialog(ThemeSettings(ThemeMode.System, dynamicColor = true))
        rule.onNodeWithTag(THEME_MODE_DARK).performClick()
        rule.waitForIdle()
        assertEquals(listOf(ThemeSettings(ThemeMode.Dark, dynamicColor = true)), changes)
        rule.onNodeWithTag(THEME_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac11_tapLight_keepsDynamicColorValue() {
        showAndOpenDialog(ThemeSettings(ThemeMode.Dark, dynamicColor = false))
        rule.onNodeWithTag(THEME_MODE_LIGHT).performClick()
        rule.waitForIdle()
        assertEquals(listOf(ThemeSettings(ThemeMode.Light, dynamicColor = false)), changes)
        rule.onNodeWithTag(THEME_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac11_tapSystem_fromDark_callsOnThemeChangeWithSystem() {
        showAndOpenDialog(ThemeSettings(ThemeMode.Dark, dynamicColor = true))
        rule.onNodeWithTag(THEME_MODE_SYSTEM).performClick()
        rule.waitForIdle()
        assertEquals(listOf(ThemeSettings(ThemeMode.System, dynamicColor = true)), changes)
    }

    @Test
    fun ac11_toggleDynamic_callsOnThemeChange_andKeepsDialogOpen() {
        val current = ThemeSettings(ThemeMode.Light, dynamicColor = true)
        showAndOpenDialog(current)
        rule.onNodeWithTag(THEME_DYNAMIC).performClick()
        rule.waitForIdle()
        assertEquals(listOf(current.copy(dynamicColor = false)), changes)
        rule.onNodeWithTag(THEME_DIALOG).assertIsDisplayed()
    }

    @Test
    fun ac11_back_closesDialog_withoutCallback() {
        showAndOpenDialog(ThemeSettings())
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()
        rule.onNodeWithTag(THEME_DIALOG).assertDoesNotExist()
        assertEquals(emptyList<ThemeSettings>(), changes)
    }
}
