package app.pixel.browser

import android.app.Activity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.pixel.browser.core.data.FakeThemeSettingsStore
import app.pixel.browser.core.data.ThemeMode
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.ui.NightModeController
import app.pixel.browser.feature.browser.BrowserTestTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Spec 011 AC12 (phần MainActivity), AC13. AC14 ở [MainActivityThemePreloadTest]. */
@RunWith(RobolectricTestRunner::class)
class MainActivityThemeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app: PixelBrowserApplication get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    private val scopes = mutableListOf<CoroutineScope>()

    private class RecordingNightModeController : NightModeController {
        val modes = mutableListOf<ThemeMode>()

        override fun apply(mode: ThemeMode) {
            modes += mode
        }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    /** Scope trên main looper của Robolectric: compose rule `waitForIdle` đẩy nó chạy. */
    private fun mainScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            scopes +=
                it
        }

    /** Gắn [store] (và [controller]) vào app trong lúc Activity đang chạy, rồi chờ recomposition. */
    private fun bind(
        store: FakeThemeSettingsStore,
        controller: NightModeController = RecordingNightModeController(),
    ) {
        rule.runOnUiThread {
            app.nightModeController = controller
            app.bindThemeStore(store, mainScope())
        }
        rule.waitForIdle()
    }

    private fun insets(activity: Activity): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)

    private fun openThemeDialog() {
        rule.onNodeWithTag(BrowserTestTags.BTN_MENU).performClick()
        rule.onNodeWithTag(BrowserTestTags.MENU_THEME).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BrowserTestTags.THEME_DIALOG).assertExists()
    }

    // --- AC12 ---

    @Test
    fun ac12_chooseDarkInDialog_writesStore_andAppliesNightModeOnce() {
        val store = FakeThemeSettingsStore(ThemeSettings(ThemeMode.System, dynamicColor = true))
        val controller = RecordingNightModeController()
        bind(store, controller)

        openThemeDialog()
        rule.onNodeWithTag(BrowserTestTags.THEME_MODE_DARK).performClick()
        rule.waitUntil(TIMEOUT_MS) { store.current.mode == ThemeMode.Dark }
        rule.waitForIdle()

        assertEquals(ThemeSettings(ThemeMode.Dark, dynamicColor = true), store.current)
        assertEquals(listOf(ThemeMode.Dark), controller.modes)
    }

    @Test
    fun ac12_changeOnlyDynamicColor_doesNotCallNightModeController() {
        val store = FakeThemeSettingsStore(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        val controller = RecordingNightModeController()
        bind(store, controller)

        openThemeDialog()
        rule.onNodeWithTag(BrowserTestTags.THEME_DYNAMIC).performClick()
        rule.waitUntil(TIMEOUT_MS) { !store.current.dynamicColor }
        rule.waitForIdle()

        assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = false), store.current)
        assertEquals(emptyList<ThemeMode>(), controller.modes)
    }

    @Test
    fun ac12_rapidDoubleToggleOfDynamicColor_endsWithOriginalValue() {
        val store = FakeThemeSettingsStore(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        val controller = RecordingNightModeController()
        bind(store, controller)

        openThemeDialog()
        rule.onNodeWithTag(BrowserTestTags.THEME_DYNAMIC).performClick()
        rule.onNodeWithTag(BrowserTestTags.THEME_DYNAMIC).performClick()
        rule.waitUntil(TIMEOUT_MS) { store.updates.size == 2 }
        rule.waitForIdle()

        assertEquals(
            listOf(
                ThemeSettings(ThemeMode.Light, dynamicColor = false),
                ThemeSettings(ThemeMode.Light, dynamicColor = true),
            ),
            store.updates,
        )
        assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = true), app.themeSettings.value)
        assertEquals(emptyList<ThemeMode>(), controller.modes)
    }

    // --- AC13 ---

    @Test
    @Config(qualifiers = "night")
    fun ac13_lightSetting_inNightConfig_usesDarkSystemBarIcons() {
        bind(FakeThemeSettingsStore(ThemeSettings(ThemeMode.Light, dynamicColor = true)))
        val insets = insets(rule.activity)
        assertTrue("status bar icons tối trên nền sáng", insets.isAppearanceLightStatusBars)
        assertTrue("navigation bar icons tối trên nền sáng", insets.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun ac13_darkSetting_inNotNightConfig_usesLightSystemBarIcons() {
        bind(FakeThemeSettingsStore(ThemeSettings(ThemeMode.Dark, dynamicColor = true)))
        val insets = insets(rule.activity)
        assertFalse(insets.isAppearanceLightStatusBars)
        assertFalse(insets.isAppearanceLightNavigationBars)
    }

    @Test
    @Config(qualifiers = "night")
    fun ac13_togglingDynamicColor_doesNotChangeSystemBarAppearance() {
        val store = FakeThemeSettingsStore(ThemeSettings(ThemeMode.Light, dynamicColor = true))
        bind(store)
        assertTrue(insets(rule.activity).isAppearanceLightStatusBars)

        runBlocking { store.update { it.copy(dynamicColor = false) } }
        rule.waitForIdle()
        assertEquals(ThemeSettings(ThemeMode.Light, dynamicColor = false), app.themeSettings.value)
        assertTrue(insets(rule.activity).isAppearanceLightStatusBars)
        assertTrue(insets(rule.activity).isAppearanceLightNavigationBars)
    }

    @Test
    fun ac13_navigationBarContrastNotEnforced() {
        bind(FakeThemeSettingsStore())
        assertFalse(rule.activity.window.isNavigationBarContrastEnforced)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
