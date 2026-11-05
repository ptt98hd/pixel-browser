package app.pixel.browser.feature.browser

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import app.pixel.browser.feature.browser.BrowserTestTags.BTN_MENU
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_RELOAD
import app.pixel.browser.feature.browser.BrowserTestTags.MENU_SET_DEFAULT
import app.pixel.browser.feature.browser.intent.RoleManagerDefaultBrowser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowRoleManager

/** Spec 006 – AC18, AC19: trình duyệt mặc định qua `RoleManager` (Robolectric, `ShadowRoleManager`). */
@RunWith(RobolectricTestRunner::class)
class DefaultBrowserTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val roleManager: RoleManager get() = context.getSystemService(RoleManager::class.java)
    private val shadowRoles: ShadowRoleManager get() = Shadow.extract(roleManager)

    private lateinit var f: BrowserFixture

    /** ShadowRoleManager ném nếu gỡ role chưa thêm, nên chỉ đổi khi trạng thái khác. */
    private fun roleAvailable(available: Boolean) {
        if (roleManager.isRoleAvailable(ROLE) == available) return
        if (available) shadowRoles.addAvailableRole(ROLE) else shadowRoles.removeAvailableRole(ROLE)
    }

    private fun roleHeld(held: Boolean) {
        if (roleManager.isRoleHeld(ROLE) == held) return
        if (held) shadowRoles.addHeldRole(ROLE) else shadowRoles.removeHeldRole(ROLE)
    }

    private fun launch() {
        f = BrowserFixture()
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
    }

    private fun openMenu() {
        rule.onNodeWithTag(BTN_MENU).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(MENU_RELOAD).assertExists()
    }

    private fun closeMenu() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Lấy (và xóa) mọi intent đã start; trả intent cuối cùng (for-result hoặc thường). */
    private fun drainStarted(): Intent? {
        val shadow = shadowOf(rule.activity)
        var last: Intent? = null
        while (true) {
            last = shadow.nextStartedActivityForResult?.intent ?: break
        }
        while (true) {
            last = shadow.nextStartedActivity ?: break
        }
        return last
    }

    private fun assertIsRoleRequest(intent: Intent?) {
        assertNotNull(intent)
        val expected = roleManager.createRequestRoleIntent(ROLE)
        assertEquals(expected.action, intent!!.action)
        assertTrue("intent $intent must equal $expected", expected.filterEquals(intent))
        assertEquals(ROLE, intent.getStringExtra(EXTRA_ROLE_NAME))
    }

    private fun tapSetDefault(): Intent {
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).performClick()
        rule.waitForIdle()
        val request = shadowOf(rule.activity).nextStartedActivityForResult
        assertNotNull("tap must start an activity for result", request)
        return request.intent
    }

    // --- AC18 ---

    @Test
    fun ac18_roleAvailableAndNotHeld_menuShowsSetDefault() {
        roleAvailable(true)
        roleHeld(false)
        launch()
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertExists()
        assertTrue(f.viewModel.uiState.value.showSetDefaultBrowser)
    }

    @Test
    fun ac18_roleHeld_menuHidesSetDefault() {
        roleAvailable(true)
        roleHeld(true)
        launch()
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertDoesNotExist()
        assertFalse(f.viewModel.uiState.value.showSetDefaultBrowser)
    }

    @Test
    fun ac18_roleUnavailable_menuHidesSetDefault() {
        roleAvailable(false)
        launch()
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertDoesNotExist()
    }

    @Test
    fun ac18_isRecomputedOnResume() {
        roleAvailable(true)
        roleHeld(false)
        launch()
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertExists()
        closeMenu()

        // Người dùng đổi trình duyệt mặc định trong Settings rồi quay lại.
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        roleHeld(true)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()

        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertDoesNotExist()
        assertFalse(f.viewModel.uiState.value.showSetDefaultBrowser)
    }

    @Test
    fun ac18_viewModel_onDefaultBrowserChanged_togglesFlag() {
        val vm = BrowserFixture().viewModel
        vm.onDefaultBrowserChanged(false)
        assertTrue(vm.uiState.value.showSetDefaultBrowser)
        vm.onDefaultBrowserChanged(true)
        assertFalse(vm.uiState.value.showSetDefaultBrowser)
    }

    // --- AC19 ---

    @Test
    fun ac19_tapSetDefault_startsRoleRequestForResult_andClosesMenu() {
        roleAvailable(true)
        roleHeld(false)
        launch()
        val intent = tapSetDefault()
        assertIsRoleRequest(intent)
        rule.onNodeWithTag(MENU_RELOAD).assertDoesNotExist()
    }

    @Test
    fun ac19_resultOk_withRoleHeld_hidesMenuItem() {
        roleAvailable(true)
        roleHeld(false)
        launch()
        val intent = tapSetDefault()
        roleHeld(true)
        rule.runOnIdle { shadowOf(rule.activity).receiveResult(intent, Activity.RESULT_OK, null) }
        rule.waitForIdle()
        assertFalse(f.viewModel.uiState.value.showSetDefaultBrowser)
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertDoesNotExist()
    }

    @Test
    fun ac19_resultCanceled_keepsMenuItem_withoutMessage() {
        roleAvailable(true)
        roleHeld(false)
        launch()
        val intent = tapSetDefault()
        rule.runOnIdle { shadowOf(rule.activity).receiveResult(intent, Activity.RESULT_CANCELED, null) }
        rule.waitForIdle()
        assertTrue(f.viewModel.uiState.value.showSetDefaultBrowser)
        assertNull(f.viewModel.uiState.value.message)
        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).assertExists()
    }

    @Test
    fun ac19_afterTwoConsecutiveCancels_opensDefaultAppsSettings() {
        roleAvailable(true)
        roleHeld(false)
        launch()
        repeat(2) {
            val intent = tapSetDefault()
            assertIsRoleRequest(intent)
            rule.runOnIdle { shadowOf(rule.activity).receiveResult(intent, Activity.RESULT_CANCELED, null) }
            rule.waitForIdle()
        }
        drainStarted()

        openMenu()
        rule.onNodeWithTag(MENU_SET_DEFAULT).performClick()
        rule.waitForIdle()

        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, drainStarted()?.action)
    }

    @Test
    fun ac19_controller_reflectsRoleAvailabilityAndHolder() {
        roleAvailable(true)
        roleHeld(false)
        val controller = RoleManagerDefaultBrowser(context)
        assertTrue(controller.isRoleAvailable)
        assertFalse(controller.isDefault())
        roleHeld(true)
        assertTrue(controller.isDefault())
        // ShadowRoleManager reports a role as available while it has a holder, so release it first.
        roleHeld(false)
        roleAvailable(false)
        assertFalse(controller.isRoleAvailable)
    }

    @Test
    fun ac19_controller_requestIntent_isRoleRequest_whenAvailable() {
        roleAvailable(true)
        roleHeld(false)
        assertIsRoleRequest(RoleManagerDefaultBrowser(context).requestIntent())
    }

    @Test
    fun ac19_controller_requestIntent_isDefaultAppsSettings_whenRoleUnavailable() {
        roleAvailable(false)
        assertEquals(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            RoleManagerDefaultBrowser(context).requestIntent().action,
        )
    }

    private companion object {
        const val ROLE = RoleManager.ROLE_BROWSER

        /** `Intent.EXTRA_ROLE_NAME` (hidden API). */
        const val EXTRA_ROLE_NAME = "android.intent.extra.ROLE_NAME"
    }
}
