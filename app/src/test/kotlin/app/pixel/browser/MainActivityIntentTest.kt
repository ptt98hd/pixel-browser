package app.pixel.browser

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import app.pixel.browser.core.tabs.FileTabSnapshotStore
import app.pixel.browser.core.tabs.TabSnapshot
import app.pixel.browser.core.tabs.TabsSnapshot
import app.pixel.browser.feature.browser.BrowserViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import java.io.File

/** Spec 006 – AC15, AC16: intent vào `MainActivity` (cold start, `onNewIntent`, recreate). */
@RunWith(RobolectricTestRunner::class)
class MainActivityIntentTest {
    private val app get() = RuntimeEnvironment.getApplication()

    /** Store có sẵn tab A (`https://a.com/`) được chọn. */
    @Before
    fun seedStore() {
        FileTabSnapshotStore(File(app.noBackupFilesDir, "tabs.json"))
            .write(TabsSnapshot(listOf(TabSnapshot(id = "a", url = "https://a.com/", title = "A")), "a"))
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun launcherIntent() =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClass(app, MainActivity::class.java)

    private fun start(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent).setup()

    /** ViewModel đã được `MainActivity` tạo (cùng key mặc định với `by viewModels`). */
    private fun ActivityController<MainActivity>.vm(): BrowserViewModel =
        ViewModelProvider(get())[BrowserViewModel::class.java]

    private fun ActivityController<MainActivity>.tabUrls(): List<String> =
        vm()
            .uiState.value.tabs
            .map { it.url }

    private fun ActivityController<MainActivity>.selectedUrl(): String = vm().uiState.value.url

    // --- AC15 ---

    @Test
    fun ac15_coldStartWithView_keepsTabA_andOpensSelectedTab() {
        val c = start(view("https://b.com"))
        val tabs =
            c
                .vm()
                .uiState.value.tabs
        assertEquals(2, tabs.size)
        assertEquals("a", tabs[0].id)
        assertEquals("https://b.com", c.selectedUrl())
        assertNotEquals(
            "a",
            c
                .vm()
                .uiState.value.selectedTabId,
        )
    }

    @Test
    fun ac15_coldStartWithLauncher_keepsTabsUnchanged() {
        val c = start(launcherIntent())
        assertEquals(listOf("https://a.com/"), c.tabUrls())
    }

    @Test
    fun ac15_onNewIntentView_addsExactlyOneTab_inSameActivity_andUpdatesIntent() {
        val c = start(launcherIntent())
        val activity = c.get()
        val before = c.tabUrls().size
        val intent = view("https://c.com")

        c.newIntent(intent)

        assertSame(activity, c.get())
        assertEquals(before + 1, c.tabUrls().size)
        assertEquals("https://c.com", c.selectedUrl())
        assertEquals("https://c.com", c.get().intent.dataString)
        assertEquals(Intent.ACTION_VIEW, c.get().intent.action)
    }

    @Test
    fun ac15_onNewIntentLauncher_keepsTabCount() {
        val c = start(view("https://b.com"))
        val before = c.tabUrls()
        c.newIntent(launcherIntent())
        assertEquals(before, c.tabUrls())
    }

    // --- AC16 ---

    @Test
    fun ac16_recreate_doesNotReprocessIntent() {
        val c = start(view("https://b.com"))
        assertEquals(2, c.tabUrls().size)
        c.recreate()
        assertEquals(2, c.tabUrls().size)
    }

    @Test
    fun ac16_intentLaunchedFromHistory_doesNotOpenTab() {
        val c = start(view("https://b.com").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertEquals(listOf("https://a.com/"), c.tabUrls())
    }

    @Test
    fun edge_explicitIntentWithForbiddenScheme_isRejected_withoutNewTab() {
        val c = start(view("file:///sdcard/x").setClass(app, MainActivity::class.java))
        assertEquals(listOf("https://a.com/"), c.tabUrls())
    }
}
