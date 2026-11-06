package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.BrowserTestTags.AUTH_CANCEL
import app.pixel.browser.feature.browser.BrowserTestTags.AUTH_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.AUTH_INSECURE_WARNING
import app.pixel.browser.feature.browser.BrowserTestTags.AUTH_OK
import app.pixel.browser.feature.browser.BrowserTestTags.AUTH_PASSWORD
import app.pixel.browser.feature.browser.BrowserTestTags.AUTH_USERNAME
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 007 AC23 – hộp thoại HTTP auth qua [BrowserRoute] + ViewModel thật. */
@RunWith(RobolectricTestRunner::class)
class HttpAuthDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    private fun prepare(pageUrl: String) {
        f = BrowserFixture()
        f.selected().visit(pageUrl)
    }

    private fun emit(
        host: String = "a.com",
        realm: String = "Admin",
    ) {
        rule.runOnIdle { f.selected().fake.simulateHttpAuth(host, realm) }
        rule.waitForIdle()
    }

    private fun launch(
        pageUrl: String = "https://a.com/",
        realm: String = "Admin",
    ) {
        prepare(pageUrl)
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        emit(realm = realm)
    }

    private fun responses() = f.selected().fake.responses

    private fun editableText(tag: String): String =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text
            .orEmpty()

    @Test
    fun ac23_dialog_showsHost_andQuotedRealm() {
        launch()
        rule.onNodeWithTag(AUTH_DIALOG).assertExists()
        rule.inDialog(AUTH_DIALOG, hasText("a.com", substring = true)).assertExists()
        rule
            .inDialog(AUTH_DIALOG, hasText("“Admin”", substring = true) or hasText("\"Admin\"", substring = true))
            .assertExists()
    }

    @Test
    fun ac23_longRealm_isCutTo100Chars() {
        val realm = "r".repeat(100) + "TAIL"
        launch(realm = realm)
        rule.inDialog(AUTH_DIALOG, hasText("r".repeat(100), substring = true)).assertExists()
        rule.inDialog(AUTH_DIALOG, hasText("TAIL", substring = true)).assertDoesNotExist()
    }

    @Test
    fun ac23_passwordField_hasPasswordSemantics() {
        launch()
        rule.onNodeWithTag(AUTH_PASSWORD).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun ac23_ok_sendsCredentials_andCloses() {
        launch()
        rule.onNodeWithTag(AUTH_USERNAME).performTextInput("u")
        rule.onNodeWithTag(AUTH_PASSWORD).performTextInput("p")
        rule.onNodeWithTag(AUTH_OK).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Credentials("u", "p"), responses()[1L])
        rule.onNodeWithTag(AUTH_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac23_cancel_dismisses() {
        launch()
        rule.onNodeWithTag(AUTH_CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        rule.onNodeWithTag(AUTH_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac23_back_dismisses() {
        launch()
        rule.onNodeWithTag(AUTH_DIALOG).assertExists()
        rule.pressSystemBack()
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        rule.onNodeWithTag(AUTH_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac23_insecureOrigin_showsWarning() {
        launch(pageUrl = "http://a.com/")
        rule.onNodeWithTag(AUTH_DIALOG).assertExists()
        rule.onNodeWithTag(AUTH_INSECURE_WARNING).assertExists()
    }

    @Test
    fun ac23_secureOrigin_hasNoWarning() {
        launch(pageUrl = "https://a.com/")
        rule.onNodeWithTag(AUTH_DIALOG).assertExists()
        rule.onNodeWithTag(AUTH_INSECURE_WARNING).assertDoesNotExist()
    }

    @Test
    fun ac23_password_isNotRestoredAfterStateRestore() {
        prepare("https://a.com/")
        val tester = StateRestorationTester(rule)
        tester.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        emit()
        rule.onNodeWithTag(AUTH_PASSWORD).performTextInput("p")
        assertEquals(1, editableText(AUTH_PASSWORD).length)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(AUTH_DIALOG).assertExists()
        assertEquals("", editableText(AUTH_PASSWORD))
    }
}
