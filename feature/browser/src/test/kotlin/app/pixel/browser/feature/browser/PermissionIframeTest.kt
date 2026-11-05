package app.pixel.browser.feature.browser

import android.Manifest.permission.CAMERA
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.SitePermission.Camera
import app.pixel.browser.feature.browser.BrowserTestTags.PERMISSION_DIALOG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Review spec 007: quyền xin từ iframe khác origin với trang chính không dùng/ghi bộ nhớ quyền (chỉ nhớ khi
 * origin xin = origin trang), và dialog hiện cả hai origin.
 */
@RunWith(RobolectricTestRunner::class)
class PermissionIframeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun fixture() =
        BrowserFixture().apply {
            selected().visit("https://a.com/page")
            gate.alreadyGranted += CAMERA
        }

    @Test
    fun iframe_rememberedAllowForFrameOrigin_isNotUsed_whenEmbedded() {
        val f = fixture()
        f.permissionMemory.set("https://ads.com", Camera, true)
        f.selected().fake.simulatePermissionRequest(setOf(Camera), "https://ads.com")
        assertTrue(f.state().pendingRequest is EngineRequest.PermissionPrompt)
    }

    @Test
    fun iframe_rememberedAllowForTopOrigin_isNotUsed_forEmbeddedFrame() {
        val f = fixture()
        f.permissionMemory.set("https://a.com", Camera, true)
        f.selected().fake.simulatePermissionRequest(setOf(Camera), "https://ads.com")
        assertTrue(f.state().pendingRequest is EngineRequest.PermissionPrompt)
    }

    @Test
    fun iframe_decision_isNotRemembered() {
        val f = fixture()
        f.selected().fake.simulatePermissionRequest(setOf(Camera), "https://ads.com")
        val p = f.state().pendingRequest!!
        f.viewModel.onPermissionDecision(p.id, allow = true)
        assertEquals(EngineResponse.Grant(setOf(Camera)), f.selected().fake.responses[p.id])
        assertNull(f.permissionMemory.get("https://ads.com", Camera))
        assertNull(f.permissionMemory.get("https://a.com", Camera))
    }

    @Test
    fun iframe_dialog_showsBothOrigins() {
        val f = fixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulatePermissionRequest(setOf(Camera), "https://ads.com") }
        rule.waitForIdle()
        rule.inDialog(PERMISSION_DIALOG, hasText("ads.com", substring = true)).assertExists()
        rule
            .inDialog(PERMISSION_DIALOG, hasText("a.com", substring = true) and !hasText("ads.com", substring = true))
            .assertExists()
    }
}
