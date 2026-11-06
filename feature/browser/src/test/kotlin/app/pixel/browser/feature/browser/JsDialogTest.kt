package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.JsDialogKind
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG_CANCEL
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG_INPUT
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG_MESSAGE
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG_OK
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG_SUPPRESS
import app.pixel.browser.feature.browser.BrowserTestTags.JS_DIALOG_TITLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 007 AC14 – hộp thoại JS qua [BrowserRoute] + ViewModel thật (Compose, Robolectric). */
@RunWith(RobolectricTestRunner::class)
class JsDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    private fun launch() {
        f = BrowserFixture()
        f.selected().visit("https://www.example.com/page")
        f.selected().fake.simulateTitle("Page Title Should Not Be Used")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun emit(
        kind: JsDialogKind,
        message: String = "hello",
        defaultValue: String? = null,
    ): EngineRequest.JsDialog {
        var r: EngineRequest.JsDialog? = null
        rule.runOnIdle { r = f.selected().fake.simulateJsDialog(kind, message, defaultValue) }
        rule.waitForIdle()
        return r!!
    }

    private fun responses() = f.selected().fake.responses

    @Test
    fun ac14_alert_showsHostTitle_message_andOnlyOk() {
        launch()
        emit(JsDialogKind.Alert, "hello world")
        rule.onNodeWithTag(JS_DIALOG).assertExists()
        val title = rule.onNodeWithTag(JS_DIALOG_TITLE).textValue()
        assertTrue(title, title.contains("example.com"))
        assertFalse(title, title.contains("Page Title"))
        rule.onNodeWithTag(JS_DIALOG_MESSAGE).assertTextIs("hello world")
        rule.onNodeWithTag(JS_DIALOG_OK).assertExists()
        rule.onNodeWithTag(JS_DIALOG_CANCEL).assertDoesNotExist()
        rule.onNodeWithTag(JS_DIALOG_INPUT).assertDoesNotExist()
    }

    @Test
    fun ac14_alertOk_respondsConfirm_andCloses() {
        launch()
        val r = emit(JsDialogKind.Alert)
        rule.onNodeWithTag(JS_DIALOG_OK).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Confirm(), responses()[r.id])
        rule.onNodeWithTag(JS_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac14_confirmAndBeforeUnload_haveOkAndCancel() {
        launch()
        for (kind in listOf(JsDialogKind.Confirm, JsDialogKind.BeforeUnload)) {
            val r = emit(kind)
            rule.onNodeWithTag(JS_DIALOG_OK).assertExists()
            rule.onNodeWithTag(JS_DIALOG_CANCEL).assertExists()
            rule.onNodeWithTag(JS_DIALOG_CANCEL).performClick()
            rule.waitForIdle()
            assertEquals(kind.name, EngineResponse.Dismiss(), responses()[r.id])
        }
    }

    @Test
    fun ac14_confirmOk_respondsConfirm() {
        launch()
        val r = emit(JsDialogKind.Confirm)
        rule.onNodeWithTag(JS_DIALOG_OK).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Confirm(), responses()[r.id])
    }

    @Test
    fun ac14_prompt_prefillsDefault_andOkSendsTypedText() {
        launch()
        val r = emit(JsDialogKind.Prompt, "name?", "def")
        val input = rule.onNodeWithTag(JS_DIALOG_INPUT)
        assertEquals("def", input.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        input.performTextReplacement("abc")
        rule.onNodeWithTag(JS_DIALOG_OK).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.PromptText("abc"), responses()[r.id])
    }

    @Test
    fun ac14_back_dismissesDialog() {
        launch()
        val r = emit(JsDialogKind.Confirm)
        rule.onNodeWithTag(JS_DIALOG).assertExists()
        rule.pressSystemBack()
        assertEquals(EngineResponse.Dismiss(), responses()[r.id])
        rule.onNodeWithTag(JS_DIALOG).assertDoesNotExist()
        assertFalse(rule.activity.isFinishing)
    }

    @Test
    fun ac14_firstDialog_hasNoSuppressCheckbox() {
        launch()
        emit(JsDialogKind.Alert)
        rule.onNodeWithTag(JS_DIALOG).assertExists()
        rule.onNodeWithTag(JS_DIALOG_SUPPRESS).assertDoesNotExist()
    }

    @Test
    fun ac14_suppressCheckbox_tickedThenOk_respondsConfirmWithSuppress() {
        launch()
        emit(JsDialogKind.Alert, "1")
        rule.onNodeWithTag(JS_DIALOG_OK).performClick()
        rule.waitForIdle()
        val second = emit(JsDialogKind.Alert, "2")
        assertTrue(second.canSuppress)
        rule.onNodeWithTag(JS_DIALOG_SUPPRESS).performClick()
        rule.onNodeWithTag(JS_DIALOG_OK).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Confirm(suppressFurther = true), responses()[second.id])
    }

    @Test
    fun ac14_veryLongMessage_isScrollable_andButtonsStayVisible() {
        launch()
        val message = "x".repeat(20_000)
        emit(JsDialogKind.Confirm, message)
        assertEquals(message, rule.onNodeWithTag(JS_DIALOG_MESSAGE).textValue())
        rule.onNodeWithTag(JS_DIALOG_OK).assertIsDisplayed()
        rule.onNodeWithTag(JS_DIALOG_CANCEL).assertIsDisplayed()
    }
}
