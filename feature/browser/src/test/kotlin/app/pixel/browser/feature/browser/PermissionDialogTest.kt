package app.pixel.browser.feature.browser

import android.Manifest.permission.CAMERA
import android.Manifest.permission.RECORD_AUDIO
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.SitePermission
import app.pixel.browser.feature.browser.BrowserTestTags.PERMISSION_ALLOW
import app.pixel.browser.feature.browser.BrowserTestTags.PERMISSION_BLOCK
import app.pixel.browser.feature.browser.BrowserTestTags.PERMISSION_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.permissionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec 007 AC15 – hộp thoại quyền qua [BrowserRoute] + ViewModel thật (gate giả). */
@RunWith(RobolectricTestRunner::class)
class PermissionDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture

    private fun launchWithPrompt(vararg permissions: SitePermission) {
        f = BrowserFixture()
        f.selected().visit("https://a.com/room")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulatePermissionRequest(permissions.toSet()) }
        rule.waitForIdle()
    }

    private fun responses() = f.selected().fake.responses

    @Test
    fun ac15_dialog_showsOriginHost_andRequestedItems() {
        launchWithPrompt(SitePermission.Camera, SitePermission.Microphone)
        rule.onNodeWithTag(PERMISSION_DIALOG).assertExists()
        rule.inDialog(PERMISSION_DIALOG, hasText("a.com", substring = true)).assertExists()
        rule.onNodeWithTag(permissionItem(SitePermission.Camera)).assertExists()
        rule.onNodeWithTag(permissionItem(SitePermission.Microphone)).assertExists()
        rule.onNodeWithTag(permissionItem(SitePermission.Location)).assertDoesNotExist()
    }

    @Test
    fun ac15_allow_asksOs_thenGrants_andClosesDialog() {
        launchWithPrompt(SitePermission.Camera, SitePermission.Microphone)
        rule.onNodeWithTag(PERMISSION_ALLOW).performClick()
        rule.waitForIdle()
        assertEquals(listOf(setOf(CAMERA, RECORD_AUDIO)), f.gate.requests)
        assertEquals(EngineResponse.Grant(setOf(SitePermission.Camera, SitePermission.Microphone)), responses()[1L])
        rule.onNodeWithTag(PERMISSION_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac15_block_dismisses_withoutAskingOs() {
        launchWithPrompt(SitePermission.Location)
        rule.onNodeWithTag(PERMISSION_BLOCK).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        assertTrue(f.gate.requests.isEmpty())
        rule.onNodeWithTag(PERMISSION_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac15_back_dismisses() {
        launchWithPrompt(SitePermission.Camera)
        rule.onNodeWithTag(PERMISSION_DIALOG).assertExists()
        rule.pressSystemBack()
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        rule.onNodeWithTag(PERMISSION_DIALOG).assertDoesNotExist()
    }
}
