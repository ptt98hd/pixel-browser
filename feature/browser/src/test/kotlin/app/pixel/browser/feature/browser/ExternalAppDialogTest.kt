package app.pixel.browser.feature.browser

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.BrowserTestTags.EXTERNAL_CANCEL
import app.pixel.browser.feature.browser.BrowserTestTags.EXTERNAL_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.EXTERNAL_OPEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/** Spec 006 – AC23/AC24 (UI): hộp thoại xác nhận mở app ngoài qua [BrowserRoute] + ViewModel thật. */
@RunWith(RobolectricTestRunner::class)
class ExternalAppDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    private fun launch() {
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun emit(
        uri: String,
        gesture: Boolean,
    ) {
        rule.runOnIdle { f.selected().fake.simulateNavigation(uri, hasUserGesture = gesture) }
        rule.waitForIdle()
    }

    private fun inDialog(matcher: SemanticsMatcher) =
        rule.onNode(matcher and (hasTestTag(EXTERNAL_DIALOG) or hasAnyAncestor(hasTestTag(EXTERNAL_DIALOG))))

    /** Back hệ thống tới cửa sổ đang ở trên cùng: dialog nếu đang hiện, nếu không thì activity. */
    @Suppress("DEPRECATION")
    private fun pressSystemBack() {
        rule.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()
            when {
                dialog is ComponentDialog && dialog.isShowing -> dialog.onBackPressedDispatcher.onBackPressed()
                dialog != null && dialog.isShowing -> dialog.onBackPressed()
                else -> rule.activity.onBackPressedDispatcher.onBackPressed()
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun ac23_withGesture_noDialog() {
        launch()
        emit("tel:123", gesture = true)
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertDoesNotExist()
        assertEquals(listOf("tel:123"), f.launcher.calls)
    }

    @Test
    fun ac24_withoutGesture_showsDialogNamingScheme() {
        launch()
        emit("tel:123", gesture = false)
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertExists()
        rule.onNodeWithTag(EXTERNAL_OPEN).assertExists()
        rule.onNodeWithTag(EXTERNAL_CANCEL).assertExists()
        inDialog(hasText("tel", substring = true, ignoreCase = true)).assertExists()
        assertTrue(f.launcher.calls.isEmpty())
    }

    @Test
    fun ac24_intentUri_dialogNamesPackage() {
        launch()
        emit("intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;end", gesture = false)
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertExists()
        inDialog(hasText("com.google.zxing.client.android", substring = true)).assertExists()
    }

    @Test
    fun ac24_tapOpen_launchesOnce_respondsDismiss_andClosesDialog() {
        launch()
        emit("tel:123", gesture = false)
        rule.onNodeWithTag(EXTERNAL_OPEN).performClick()
        rule.waitForIdle()
        assertEquals(listOf("tel:123"), f.launcher.calls)
        assertEquals(EngineResponse.Dismiss(), f.selected().fake.responses[1L])
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertDoesNotExist()
        assertNull(f.viewModel.uiState.value.externalPrompt)
    }

    @Test
    fun ac24_tapCancel_doesNotLaunch_respondsDismiss_andClosesDialog() {
        launch()
        emit("tel:123", gesture = false)
        rule.onNodeWithTag(EXTERNAL_CANCEL).performClick()
        rule.waitForIdle()
        assertTrue(f.launcher.calls.isEmpty())
        assertEquals(EngineResponse.Dismiss(), f.selected().fake.responses[1L])
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac24_back_dismissesDialog_withoutLaunch() {
        launch()
        emit("tel:123", gesture = false)
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertExists()
        pressSystemBack()
        assertTrue(f.launcher.calls.isEmpty())
        assertEquals(EngineResponse.Dismiss(), f.selected().fake.responses[1L])
        rule.onNodeWithTag(EXTERNAL_DIALOG).assertDoesNotExist()
        assertFalse(rule.activity.isFinishing)
    }
}
