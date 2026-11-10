package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.data.site.DesktopSiteData
import app.pixel.browser.core.data.site.DesktopSitePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 015 AC22 – trang cài đặt "Trang web máy tính" (Robolectric + Compose). */
@RunWith(RobolectricTestRunner::class)
class DesktopSiteSettingsPageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val persisted = mutableListOf<DesktopSiteData>()

    private fun show(data: DesktopSiteData = DesktopSiteData()): DesktopSitePolicy {
        val policy = DesktopSitePolicy(data) { persisted += it }
        rule.setContent { MaterialTheme { DesktopSiteSettingsPage(policy) } }
        rule.waitForIdle()
        return policy
    }

    private val three = DesktopSiteData(sites = linkedMapOf("a.com" to true, "b.com" to true, "c.com" to true))

    @Test
    fun ac22_defaultSwitch_isOff_whenDefaultMobile() {
        show()
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).assertIsOff()
    }

    @Test
    fun ac22_defaultSwitch_isOn_whenDefaultDesktop() {
        show(DesktopSiteData(defaultDesktop = true))
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).assertIsOn()
    }

    @Test
    fun ac22_toggleDefault_callsSetDefaultOnce_andUpdatesSwitch() {
        val policy = show()
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).performClick()
        rule.waitForIdle()
        assertTrue(policy.defaultDesktop.value)
        assertEquals(listOf(DesktopSiteData(defaultDesktop = true)), persisted)
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).assertIsOn()
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).performClick()
        rule.waitForIdle()
        assertFalse(policy.defaultDesktop.value)
        assertEquals(2, persisted.size)
    }

    @Test
    fun ac22_overrides_showsCount() {
        show(three)
        assertTrue(rule.onNodeWithTag(SettingsTestTags.DESKTOP_OVERRIDES).textValue().contains("3"))
    }

    @Test
    fun ac22_overrides_showsZero_whenNone() {
        show()
        assertTrue(rule.onNodeWithTag(SettingsTestTags.DESKTOP_OVERRIDES).textValue().contains("0"))
    }

    @Test
    fun ac22_clear_isDisabled_whenNoOverrides() {
        show()
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_CLEAR).assertExists().assertIsNotEnabled()
    }

    @Test
    fun ac22_clear_isEnabled_whenOverridesExist_tapClearsThem() {
        val policy = show(three)
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_CLEAR).assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(0, policy.overrideCount.value)
        assertEquals(DesktopSiteData(), persisted.last())
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_CLEAR).assertIsNotEnabled()
        assertTrue(rule.onNodeWithTag(SettingsTestTags.DESKTOP_OVERRIDES).textValue().contains("0"))
    }

    @Test
    fun ac22_clear_keepsDefault() {
        val policy = show(DesktopSiteData(defaultDesktop = true, sites = mapOf("a.com" to false)))
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_CLEAR).performClick()
        rule.waitForIdle()
        assertTrue(policy.defaultDesktop.value)
        rule.onNodeWithTag(SettingsTestTags.DESKTOP_DEFAULT).assertIsOn()
    }
}
