package app.pixel.browser

import android.content.MutableContextWrapper
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.data.privacy.FakePrivacySettingsStore
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.FakeProfileGateway
import app.pixel.browser.core.engine.PrivateSpace
import app.pixel.browser.core.engine.RetryScheduler
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Spec 016 (review): quét profile sót khi rảnh sau khung hình đầu; hộp thoại thừa kế FLAG_SECURE của cửa sổ. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PrivateSweepAndDialogTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun bind() {
        app.bindHistoryStore(InMemoryHistoryStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindBookmarkRepository(InMemoryBookmarkRepository())
        app.bindPrivacyStore(FakePrivacySettingsStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindBaseSessionFactory { profile -> FakeEngineSession(isPrivate = profile != null) }
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            scenario = it
            rule.waitForIdle()
        }

    @Test
    fun staleProfiles_areSweptAfterFirstDraw_withoutOpeningAPrivateTab() {
        val gateway = FakeProfileGateway(names = listOf("Default", "private_old", "work"))
        app.bindPrivateSpace(PrivateSpace(gateway, scheduler = RetryScheduler { _, _ -> }))
        launch()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf("private_old"), gateway.deleted)
        assertTrue(gateway.appliedProfiles.isEmpty())
    }

    @Test
    fun dialogOfPrivateTab_inheritsFlagSecure() {
        app.bindPrivateSpace(PrivateSpace(FakeProfileGateway(), scheduler = RetryScheduler { _, _ -> }))
        val s = launch()
        lateinit var vm: BrowserViewModel
        s.onActivity { vm = ViewModelProvider(it)[BrowserViewModel::class.java] }
        rule.onNodeWithTag(BrowserTestTags.BTN_MENU).performClick()
        rule.onNodeWithTag(BrowserTestTags.MENU_NEW_PRIVATE_TAB).performClick()
        rule.waitForIdle()
        assertTrue(vm.uiState.value.selectedIsPrivate)

        rule.onNodeWithTag(BrowserTestTags.BTN_MENU).performClick()
        rule.onNodeWithTag(BrowserTestTags.MENU_THEME).performClick()
        rule.waitForIdle()

        val dialog =
            org.robolectric.shadows.ShadowDialog
                .getLatestDialog()
        assertNotNull("theme dialog must be showing", dialog)
        assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
}
