package app.pixel.browser.feature.browser

import android.Manifest.permission.CAMERA
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.web.FileChooserLauncher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Review spec 007 (BLOCKER): kết quả picker chỉ được chuyển cho trang nếu là `content://` của app khác, hoặc đúng
 * URI chụp ảnh mà chính launcher đã tạo. `file://`, đường dẫn trần và FileProvider của chính app bị bỏ.
 */
@RunWith(RobolectricTestRunner::class)
class FileChooserUriFilterTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val responses = mutableListOf<EngineResponse>()

    private fun run(
        request: EngineRequest.FileChooser,
        registry: TestActivityResultRegistry,
        captureTarget: Uri = Uri.parse("content://app.pixel.browser.fileprovider/captures/c1.jpg"),
    ) {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                FileChooserLauncher(
                    request = request,
                    runtimePermissions = FakePermissionGate(alreadyGranted = mutableSetOf(CAMERA)),
                    onResponse = { responses += it },
                    onUnavailable = {},
                    captureUri = { captureTarget },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun single(accept: List<String> = listOf("image/*")) =
        EngineRequest.FileChooser(1, "https://a.com", accept, allowMultiple = false, capture = false)

    private fun multiple() =
        EngineRequest.FileChooser(1, "https://a.com", emptyList(), allowMultiple = true, capture = false)

    private val ownProvider get() = "content://${rule.activity.packageName}.fileprovider"

    @Test
    fun fileUri_isDropped_andDismissed() {
        run(single(), TestActivityResultRegistry { _, _ -> Uri.parse("file:///data/data/app.pixel.browser/x.db") })
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
    }

    @Test
    fun barePath_isDropped_andDismissed() {
        run(single(), TestActivityResultRegistry { _, _ -> Uri.parse("/data/data/app.pixel.browser/x.db") })
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
    }

    @Test
    fun ownFileProviderUri_isDropped_andDismissed() {
        run(single(), TestActivityResultRegistry { _, _ -> Uri.parse("$ownProvider/captures/old.jpg") })
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
    }

    @Test
    fun mixedResult_keepsOnlyForeignContentUris() {
        val good = Uri.parse("content://media/1")
        run(
            multiple(),
            TestActivityResultRegistry { _, _ ->
                listOf(Uri.parse("file:///sdcard/a"), good, Uri.parse("$ownProvider/captures/old.jpg"))
            },
        )
        assertEquals(listOf<EngineResponse>(EngineResponse.Files(listOf(good.toString()))), responses)
    }

    @Test
    fun ownCaptureTarget_isAccepted_afterTakePicture() {
        val target = Uri.parse("$ownProvider/captures/new.jpg")
        run(
            EngineRequest.FileChooser(1, "https://a.com", listOf("image/*"), allowMultiple = false, capture = true),
            TestActivityResultRegistry { _, _ -> true },
            captureTarget = target,
        )
        assertEquals(listOf<EngineResponse>(EngineResponse.Files(listOf(target.toString()))), responses)
    }
}
