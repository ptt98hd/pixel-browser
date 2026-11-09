package app.pixel.browser.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 014 AC19 (phần `:feature:home`) – ô tìm kiếm hiện tên engine hiện tại. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomePageSearchEngineTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(name: String?) {
        val config = HomeConfig(shortcuts = HomeDefaults.shortcuts)
        val actions = HomeActions(onOpenUrl = {}, onSearchClick = {})
        rule.setContent {
            MaterialTheme {
                if (name == null) {
                    HomePage(config, actions, Modifier.fillMaxSize())
                } else {
                    HomePage(config, actions, Modifier.fillMaxSize(), searchEngineName = name)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun placeholderText() = rule.onNodeWithTag(HomeTestTags.HOME_SEARCH).textValue()

    private fun description() = rule.onNodeWithTag(HomeTestTags.HOME_SEARCH).contentDescriptions().joinToString(" ")

    private fun genericPlaceholder() = rule.activity.getString(R.string.home_search_placeholder)

    @Test
    fun ac19_withEngineName_placeholderMentionsEngine() {
        show("DuckDuckGo")
        assertTrue("placeholder was: ${placeholderText()}", "DuckDuckGo" in placeholderText())
    }

    @Test
    fun ac19_withEngineName_contentDescriptionMentionsEngine() {
        show("Brave")
        assertTrue("description was: ${description()}", "Brave" in description())
    }

    @Test
    fun ac19_googleName_isShown_andNotOtherEngines() {
        show("Google")
        assertTrue("Google" in placeholderText())
        assertTrue("DuckDuckGo" !in placeholderText())
    }

    @Test
    fun ac19_customEngineNameWithEmoji_isShownVerbatim() {
        show("Kagi 🔎")
        assertTrue("Kagi 🔎" in placeholderText())
    }

    @Test
    fun ac19_emptyName_usesGenericPlaceholder() {
        show("")
        assertEquals(genericPlaceholder(), placeholderText())
    }

    @Test
    fun ac19_defaultParameter_usesGenericPlaceholder() {
        show(null)
        assertEquals(genericPlaceholder(), placeholderText())
    }

    @Test
    fun ac19_namedPlaceholder_differsFromGeneric() {
        show("Bing")
        assertTrue(placeholderText() != genericPlaceholder())
    }
}
