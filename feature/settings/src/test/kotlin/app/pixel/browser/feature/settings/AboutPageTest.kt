package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 014 AC16 – trang Giới thiệu. */
@RunWith(RobolectricTestRunner::class)
class AboutPageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = RecordingScope()
    private val info =
        AppInfo(versionName = "1.2.3", versionCode = 42L, sourceUrl = "https://github.com/example/pixel-browser")

    private fun show() {
        rule.setContent { MaterialTheme { AboutPage(info, scope) } }
        rule.waitForIdle()
    }

    @Test
    fun ac16_version_showsVersionNameAndCode() {
        show()
        val text = rule.onNodeWithTag(SettingsTestTags.ABOUT_VERSION).textValue()
        assertTrue(text, "1.2.3" in text && "42" in text)
    }

    @Test
    fun ac16_license_showsGpl3() {
        show()
        assertTrue("GPL-3.0" in rule.onNodeWithTag(SettingsTestTags.ABOUT_LICENSE).textValue())
    }

    @Test
    fun ac16_source_opensSourceUrl() {
        show()
        rule.onNodeWithTag(SettingsTestTags.ABOUT_SOURCE).performClick()
        assertEquals(listOf(info.sourceUrl), scope.urls)
    }

    @Test
    fun ac16_licensesList_existsAndListsEveryLibraryWithNameAndSpdx() {
        show()
        rule.onNodeWithTag(SettingsTestTags.ABOUT_LICENSES_LIST).assertExists()
        val first = Licenses.all.first()
        val node = rule.onNodeWithTag(SettingsTestTags.aboutLicense(0))
        node.performScrollTo()
        val text = node.textValue()
        assertTrue(text, first.name in text && first.license in text)
    }

    @Test
    fun ac16_tapLicenseEntry_opensItsUrl() {
        show()
        val entry = Licenses.all.first()
        rule.onNodeWithTag(SettingsTestTags.aboutLicense(0)).performScrollTo().performClick()
        assertEquals(listOf(entry.url), scope.urls)
    }
}
