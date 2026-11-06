package app.pixel.browser.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Review spec 010 – AC13: import ảnh lỗi hiện snackbar (không bị hủy bởi `onMessageShown`). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeRouteMessageTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun imageImportFailure_showsSnackbar_andClearsMessage() {
        val vm =
            HomeViewModel(FakeHomeConfigStore(configOf(2)), FakeImageImporter(result = null), Dispatchers.Unconfined)
        rule.setContent { MaterialTheme { HomeRoute(vm, onOpenUrl = {}, onSearchClick = {}) } }
        rule.waitForIdle()

        rule.runOnIdle { vm.onImagePicked("content://x/1") }
        rule.waitForIdle()

        rule.onNodeWithText(rule.activity.getString(R.string.home_message_image_failed)).assertExists()
        assertNull(vm.message.value)
    }
}
