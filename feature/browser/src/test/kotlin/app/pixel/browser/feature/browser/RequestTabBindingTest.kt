package app.pixel.browser.feature.browser

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.core.engine.JsDialogKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Registry giữ lại các lần launch; test tự trả kết quả sau (như picker thật mở lâu). */
class DeferredActivityResultRegistry : ActivityResultRegistry() {
    val launches = mutableListOf<Int>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += requestCode
    }

    fun deliver(
        requestCode: Int,
        result: Any?,
    ) {
        dispatchResult(requestCode, result)
    }
}

/** Review spec 007: response của dialog/picker gắn với tab lúc hiện, không rơi sang tab được chọn sau đó. */
@RunWith(RobolectricTestRunner::class)
class RequestTabBindingTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fileChooserResult_afterTabSwitch_neverReachesOtherTab() {
        val f = BrowserFixture()
        val a = f.tabManager.selectedTabId.value!!
        f.selected().visit("https://a.com/")
        val b = f.tabManager.openTab("https://b.com/", select = false).id
        f.tabManager
            .spy(b)
            .fake
            .simulatePageFinished("https://b.com/")
        val registry = DeferredActivityResultRegistry()
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                BrowserRoute(viewModel = f.viewModel)
            }
        }
        rule.waitForIdle()
        val fakeA = f.tabManager.spy(a).fake
        val fakeB = f.tabManager.spy(b).fake
        // Cùng id request (1) ở hai tab.
        rule.runOnIdle {
            fakeA.simulateFileChooser(listOf("image/*"))
            fakeB.simulateFileChooser(listOf("image/*"))
        }
        rule.waitForIdle()
        assertEquals(1, registry.launches.size)
        val pickerForA = registry.launches.single()

        rule.runOnIdle { f.viewModel.onSelectTab(b) }
        rule.waitForIdle()
        rule.runOnIdle { registry.deliver(pickerForA, Uri.parse("content://media/secret-of-a")) }
        rule.waitForIdle()

        assertNull("tab B không được nhận file chọn cho tab A", fakeB.responses[1L])
        assertTrue("tab B mở picker riêng", registry.launches.size == 2)
    }

    @Test
    fun onRespond_withTabId_ignoresTabWithoutThatRequest() {
        val f = BrowserFixture()
        val a = f.tabManager.selectedTabId.value!!
        f.selected().visit("https://a.com/")
        val b = f.tabManager.openTab("https://b.com/", select = true).id
        val dialog =
            f.tabManager
                .spy(a)
                .fake
                .simulateJsDialog(JsDialogKind.Confirm, "a?")!!
        f.viewModel.onRespond(b, dialog.id, EngineResponse.Confirm())
        assertTrue(
            f.tabManager
                .spy(a)
                .fake.responses
                .isEmpty(),
        )
        f.viewModel.onRespond(a, dialog.id, EngineResponse.Confirm())
        assertEquals(
            EngineResponse.Confirm(),
            f.tabManager
                .spy(a)
                .fake.responses[dialog.id],
        )
    }
}
