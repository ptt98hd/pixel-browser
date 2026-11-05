package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.feature.browser.BrowserTestTags.EXTERNAL_DIALOG
import app.pixel.browser.feature.browser.intent.ExternalIntents
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 006 review: nội dung hộp thoại lấy từ cùng parse mà launcher dùng, không regex trên URI thô. */
@RunWith(RobolectricTestRunner::class)
class ExternalAppDialogTargetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    private fun show(uri: String) {
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
        // Mô tả giống launcher Android thật.
        f.launcher.describeResult = ExternalIntents.describe(uri, "app.pixel.browser")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulateNavigation(uri, hasUserGesture = false) }
        rule.waitForIdle()
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertExists()
    }

    private fun inDialog(text: String) =
        rule.onNode(
            hasText(text, substring = true) and
                (hasTestTag(EXTERNAL_DIALOG) or hasAnyAncestor(hasTestTag(EXTERNAL_DIALOG))),
        )

    @Test
    fun selectorPackage_isNotShown_dataSchemeIs() {
        show("intent://x#Intent;scheme=evil;SEL;package=com.mybank;end")
        inDialog("com.mybank").assertDoesNotExist()
        inDialog("evil").assertExists()
    }

    @Test
    fun duplicatePackage_showsLaunchedPackageOnly() {
        val uri = "intent://x#Intent;scheme=zxing;package=com.mybank;package=com.evil;end"
        show(uri)
        val launched = ExternalIntents.parse(uri, "app.pixel.browser")!!.intent!!.`package`!!
        assertEquals("com.evil", launched)
        inDialog(launched).assertExists()
        inDialog("com.mybank").assertDoesNotExist()
    }

    @Test
    fun intentUri_showsDataScheme_notIntent() {
        show("intent://scan/#Intent;scheme=zxing;end")
        inDialog("zxing").assertExists()
        inDialog("“intent”").assertDoesNotExist()
    }
}
