package app.pixel.browser.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.input.ImeAction
import app.pixel.browser.core.data.search.CustomSearchEngine
import app.pixel.browser.core.data.search.SearchEngineIds
import app.pixel.browser.core.data.search.SearchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 014 AC13 (chọn engine) và AC14 (engine tùy chỉnh – UI). */
@RunWith(RobolectricTestRunner::class)
class SearchEnginePageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var current by mutableStateOf(SearchSettings())
    private val transforms = mutableListOf<(SearchSettings) -> SearchSettings>()

    private val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")
    private val builtIn = listOf("google", "duckduckgo", "brave", "bing")
    private val allOptions = builtIn + "custom"

    private fun show(initial: SearchSettings = SearchSettings()) {
        current = initial
        rule.setContent {
            MaterialTheme {
                SearchEnginePage(current, onChange = { t ->
                    transforms += t
                    current = t(current)
                })
            }
        }
        rule.waitForIdle()
    }

    private fun option(id: String) = rule.onNodeWithTag(SettingsTestTags.searchEngineOption(id))

    private fun openCustom() {
        option("custom").performClick()
        rule.waitForIdle()
    }

    private fun type(
        name: String? = null,
        template: String? = null,
    ) {
        name?.let { rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).performTextInput(it) }
        template?.let { rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).performTextInput(it) }
        rule.waitForIdle()
    }

    private fun save() {
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_SAVE).performClick()
        rule.waitForIdle()
    }

    private fun errorText() = rule.onNodeWithTag(SettingsTestTags.CUSTOM_ERROR).textValue()

    // --- AC13 ---

    @Test
    fun ac13_optionsInOrder_googleDdgBraveBingCustom() {
        show()
        val tops = allOptions.map { option(it).getBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(5, tops.distinct().size)
    }

    private fun assertSelectedFollows(id: String) {
        show(SearchSettings(selectedId = id))
        for (other in allOptions) {
            if (other == id) option(other).assertIsSelected() else option(other).assertIsNotSelected()
        }
    }

    @Test
    fun ac13_selectedOptionFollowsEngine_google() = assertSelectedFollows("google")

    @Test
    fun ac13_selectedOptionFollowsEngine_duckduckgo() = assertSelectedFollows("duckduckgo")

    @Test
    fun ac13_selectedOptionFollowsEngine_brave() = assertSelectedFollows("brave")

    @Test
    fun ac13_selectedOptionFollowsEngine_bing() = assertSelectedFollows("bing")

    @Test
    fun ac13_options_areRadioButtons() {
        show()
        for (id in allOptions) {
            assertEquals(Role.RadioButton, option(id).fetchSemanticsNode().config[SemanticsProperties.Role])
        }
    }

    @Test
    fun ac13_customSelectedAndValid_marksCustom() {
        show(SearchSettings(SearchEngineIds.CUSTOM, kagi))
        option("custom").assertIsSelected()
        option("google").assertIsNotSelected()
    }

    @Test
    fun ac13_brokenCustomStored_selectionShowsGoogle() {
        show(SearchSettings(SearchEngineIds.CUSTOM, CustomSearchEngine("Broken", "https://a.com/")))
        option("google").assertIsSelected()
        option("custom").assertIsNotSelected()
    }

    @Test
    fun ac13_tapBrave_callsOnChange_selectingBrave_keepingCustom() {
        show(SearchSettings("bing", kagi))
        option("brave").performClick()
        assertEquals(1, transforms.size)
        assertEquals(SearchSettings("brave", kagi), transforms.single()(SearchSettings("bing", kagi)))
        option("brave").assertIsSelected()
    }

    @Test
    fun ac13_showsNameAndHost_notFullTemplate() {
        show()
        val duck = option("duckduckgo").textValue()
        assertTrue(duck, "DuckDuckGo" in duck && "duckduckgo.com" in duck)
        assertFalse(duck, "%s" in duck || "?q=" in duck)
        val bing = option("bing").textValue()
        assertTrue(bing, "www.bing.com" in bing)
    }

    @Test
    fun ac13_customOption_showsSavedNameAndHost() {
        show(SearchSettings("google", kagi))
        val text = option("custom").textValue()
        assertTrue(text, "Kagi" in text && "kagi.com" in text)
        assertFalse(text, "%s" in text)
    }

    // --- AC14 ---

    @Test
    fun ac14_tapCustom_showsFields_emptyWhenNoCustom() {
        show()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).assertDoesNotExist()
        openCustom()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).assertExists()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).assertExists()
        assertEquals("", rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).editableText())
        assertEquals("", rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).editableText())
    }

    @Test
    fun ac14_tapCustom_prefillsExistingCustom() {
        show(SearchSettings("google", kagi))
        openCustom()
        assertEquals("Kagi", rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).editableText())
        assertEquals(kagi.template, rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).editableText())
    }

    @Test
    fun ac14_brokenStoredCustom_isPrefilledForEditing() {
        val broken = CustomSearchEngine("Broken", "https://a.com/")
        show(SearchSettings(SearchEngineIds.CUSTOM, broken))
        openCustom()
        assertEquals("Broken", rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).editableText())
        assertEquals("https://a.com/", rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).editableText())
    }

    @Test
    fun ac14_save_disabledWhileAnyFieldEmpty() {
        show()
        openCustom()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_SAVE).assertIsNotEnabled()
        type(name = "Kagi")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_SAVE).assertIsNotEnabled()
        type(template = "kagi.com/search?q=%s")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_SAVE).assertIsEnabled()
    }

    @Test
    fun ac14_save_disabledWhenOnlyTemplateFilled() {
        show()
        openCustom()
        type(template = "kagi.com/search?q=%s")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_SAVE).assertIsNotEnabled()
    }

    @Test
    fun ac14_preview_isLocalSearchUrlOfPixelBrowser() {
        show()
        openCustom()
        type("Kagi", "kagi.com/search?q=%s")
        assertTrue(
            rule.onNodeWithTag(SettingsTestTags.CUSTOM_PREVIEW).textValue(),
            "https://kagi.com/search?q=pixel%20browser" in
                rule.onNodeWithTag(SettingsTestTags.CUSTOM_PREVIEW).textValue(),
        )
    }

    @Test
    fun ac14_validSave_callsOnChange_withNormalizedCustomSelected() {
        show()
        openCustom()
        type("Kagi", "kagi.com/search?q=%s")
        save()
        assertEquals(1, transforms.size)
        assertEquals(
            SearchSettings(SearchEngineIds.CUSTOM, CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")),
            transforms.single()(SearchSettings()),
        )
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_ERROR).assertDoesNotExist()
    }

    @Test
    fun ac14_save_trimsInput() {
        show()
        openCustom()
        type("  Kagi  ", " https://kagi.com/search?q=%s ")
        save()
        assertEquals(
            SearchSettings(SearchEngineIds.CUSTOM, kagi),
            transforms.single()(SearchSettings()),
        )
    }

    @Test
    fun ac14_invalidInputs_showDistinctErrors_noOnChange_fieldsStayOpen() {
        val cases =
            listOf(
                "NoPlaceholder" to ("Kagi" to "https://a.com/"),
                "MultiplePlaceholders" to ("Kagi" to "https://a.com/?q=%s&r=%s"),
                "InvalidUrl" to ("Kagi" to "https://%s.a.com/"),
                "NameTooLong" to ("a".repeat(41) to "https://a.com/?q=%s"),
                "TemplateTooLong" to ("Kagi" to ("https://a.com/?q=%s&x=" + "a".repeat(2100))),
            )
        show()
        openCustom()
        val messages = mutableMapOf<String, String>()
        for ((label, input) in cases) {
            rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).performTextReplacement(input.first)
            rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).performTextReplacement(input.second)
            save()
            rule.onNodeWithTag(SettingsTestTags.CUSTOM_ERROR).assertExists()
            messages[label] = errorText()
            assertTrue("$label: error text empty", messages.getValue(label).isNotBlank())
            assertTrue("$label: no onChange expected", transforms.isEmpty())
            rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).assertExists()
            rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).assertExists()
        }
        assertEquals("messages must differ per error kind: $messages", cases.size, messages.values.distinct().size)
    }

    @Test
    fun ac14_error_disappears_afterFixingAndSaving() {
        show()
        openCustom()
        type("Kagi", "https://a.com/")
        save()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_ERROR).assertExists()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).performTextInput("?q=%s")
        save()
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_ERROR).assertDoesNotExist()
        assertEquals(1, transforms.size)
    }

    @Test
    fun ac14_httpTemplate_showsWarning_andCanBeSaved() {
        show()
        openCustom()
        type("Local", "http://a.com/?q=%s")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_HTTP_WARNING).assertExists()
        save()
        assertEquals(
            SearchSettings(SearchEngineIds.CUSTOM, CustomSearchEngine("Local", "http://a.com/?q=%s")),
            transforms.single()(SearchSettings()),
        )
    }

    @Test
    fun ac14_httpsTemplate_hasNoWarning() {
        show()
        openCustom()
        type("Kagi", "https://kagi.com/search?q=%s")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_HTTP_WARNING).assertDoesNotExist()
    }

    @Test
    fun ac14_tapCustomWithoutSaving_doesNotChangeEngine() {
        show()
        openCustom()
        type("Kagi", "kagi.com/search?q=%s")
        assertTrue(transforms.isEmpty())
        assertEquals(SearchSettings(), current)
    }

    @Test
    fun ac14_imeDone_savesWhenValid() {
        show()
        openCustom()
        type("Kagi", "kagi.com/search?q=%s")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).performImeAction()
        rule.waitForIdle()
        assertEquals(1, transforms.size)
    }

    @Test
    fun ac14_imeDone_doesNotSaveWhenInvalid() {
        show()
        openCustom()
        type("Kagi", "https://a.com/")
        rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).performImeAction()
        rule.waitForIdle()
        assertTrue(transforms.isEmpty())
    }

    @Test
    fun ac14_templateField_usesImeActionDone() {
        show()
        openCustom()
        val action =
            rule
                .onNodeWithTag(
                    SettingsTestTags.CUSTOM_TEMPLATE,
                ).fetchSemanticsNode()
                .config[SemanticsProperties.ImeAction]
        assertEquals(ImeAction.Done, action)
    }

    @Test
    fun ac14_typedText_survivesStateRestoration() {
        val restoration = StateRestorationTester(rule)
        current = SearchSettings()
        restoration.setContent {
            MaterialTheme { SearchEnginePage(current, onChange = { t -> transforms += t }) }
        }
        openCustom()
        type("Kagi", "kagi.com/search?q=%s")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals("Kagi", rule.onNodeWithTag(SettingsTestTags.CUSTOM_NAME).editableText())
        assertEquals("kagi.com/search?q=%s", rule.onNodeWithTag(SettingsTestTags.CUSTOM_TEMPLATE).editableText())
    }

    @Test
    fun ac14_noEngineChange_untilSave_evenAfterTypingInvalid() {
        show(SearchSettings("brave", kagi))
        openCustom()
        type(template = "x")
        assertNotEquals(SearchEngineIds.CUSTOM, current.selectedId)
    }
}
