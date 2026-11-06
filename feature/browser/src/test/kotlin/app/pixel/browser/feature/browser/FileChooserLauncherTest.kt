package app.pixel.browser.feature.browser

import android.Manifest.permission.CAMERA
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.web.FileChooserLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Ghi lại mọi lần launch và trả kết quả do test quyết định (theo hướng dẫn kiểm thử Activity Result API). */
class TestActivityResultRegistry(
    private val result: (ActivityResultContract<*, *>, Any?) -> Any?,
) : ActivityResultRegistry() {
    data class Launch(
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

    val launches = mutableListOf<Launch>()

    /** Nếu khác null, launch ném lỗi này (vd. [ActivityNotFoundException]). */
    var failure: RuntimeException? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        failure?.let { throw it }
        launches += Launch(contract, input)
        dispatchResult(requestCode, result(contract, input))
    }
}

/** Spec 007 AC19 – mở trình chọn file qua `ActivityResultRegistry` (Compose, Robolectric). */
@RunWith(RobolectricTestRunner::class)
class FileChooserLauncherTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val responses = mutableListOf<EngineResponse>()
    private var unavailable = 0
    private val captureTarget = Uri.parse("content://app.pixel.browser.fileprovider/captures/c1.jpg")
    private val uri1 = Uri.parse("content://media/1")
    private val uri2 = Uri.parse("content://media/2")

    private fun chooser(
        accept: List<String>,
        multiple: Boolean = false,
        capture: Boolean = false,
    ) = EngineRequest.FileChooser(7, "https://a.com", accept, multiple, capture)

    private fun run(
        request: EngineRequest.FileChooser,
        registry: TestActivityResultRegistry,
        gate: FakePermissionGate = FakePermissionGate(),
    ) {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                FileChooserLauncher(
                    request = request,
                    runtimePermissions = gate,
                    onResponse = { responses += it },
                    onUnavailable = { unavailable++ },
                    captureUri = { captureTarget },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun ac19_getContent_launchesWithMime_andReturnsUri() {
        val registry = TestActivityResultRegistry { _, _ -> uri1 }
        run(chooser(listOf("image/*")), registry)
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.GetContent)
        assertEquals("image/*", launch.input)
        assertEquals(listOf<EngineResponse>(EngineResponse.Files(listOf(uri1.toString()))), responses)
    }

    @Test
    fun ac19_openMultiple_launchesWithMimeTypes_andReturnsAllUris() {
        val registry = TestActivityResultRegistry { _, _ -> listOf(uri1, uri2) }
        run(chooser(listOf("image/*", "application/pdf"), multiple = true), registry)
        val input = registry.launches.single().input
        assertTrue("input: $input", input is Array<*>)
        assertEquals(listOf("image/*", "application/pdf"), (input as Array<*>).toList())
        assertEquals(
            listOf<EngineResponse>(EngineResponse.Files(listOf(uri1.toString(), uri2.toString()))),
            responses,
        )
    }

    @Test
    fun ac19_nullResult_dismisses() {
        run(chooser(listOf("image/*")), TestActivityResultRegistry { _, _ -> null })
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
    }

    @Test
    fun ac19_emptyMultipleResult_dismisses() {
        run(chooser(emptyList(), multiple = true), TestActivityResultRegistry { _, _ -> emptyList<Uri>() })
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
    }

    @Test
    fun ac19_takePicture_withCameraPermission_launchesWithCaptureUri_andReturnsIt() {
        val registry = TestActivityResultRegistry { _, _ -> true }
        val gate = FakePermissionGate(alreadyGranted = mutableSetOf(CAMERA))
        run(chooser(listOf("image/*"), capture = true), registry, gate)
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.TakePicture)
        assertEquals(captureTarget, launch.input)
        assertTrue(gate.requests.isEmpty())
        assertEquals(listOf<EngineResponse>(EngineResponse.Files(listOf(captureTarget.toString()))), responses)
    }

    @Test
    fun ac19_takePicture_cancelled_dismisses() {
        val gate = FakePermissionGate(alreadyGranted = mutableSetOf(CAMERA))
        run(chooser(listOf("image/*"), capture = true), TestActivityResultRegistry { _, _ -> false }, gate)
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
    }

    @Test
    fun ac19_takePicture_withoutCameraPermission_asksGate_thenTakesPicture() {
        val registry = TestActivityResultRegistry { _, _ -> true }
        val gate = FakePermissionGate()
        run(chooser(listOf("image/*"), capture = true), registry, gate)
        assertEquals(listOf(setOf(CAMERA)), gate.requests)
        assertTrue(registry.launches.single().contract is ActivityResultContracts.TakePicture)
    }

    @Test
    fun ac19_takePicture_cameraDenied_fallsBackToGetContentImage() {
        val registry = TestActivityResultRegistry { _, _ -> uri1 }
        val gate = FakePermissionGate(grantOnRequest = { emptySet() })
        run(chooser(listOf("image/*"), capture = true), registry, gate)
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.GetContent)
        assertEquals("image/*", launch.input)
        assertEquals(listOf<EngineResponse>(EngineResponse.Files(listOf(uri1.toString()))), responses)
    }

    @Test
    fun ac19_captureVideo_launchesWithCaptureUri() {
        val registry = TestActivityResultRegistry { _, _ -> true }
        val gate = FakePermissionGate(alreadyGranted = mutableSetOf(CAMERA))
        run(chooser(listOf("video/*"), capture = true), registry, gate)
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.CaptureVideo)
        assertEquals(captureTarget, launch.input)
        assertEquals(listOf<EngineResponse>(EngineResponse.Files(listOf(captureTarget.toString()))), responses)
    }

    @Test
    fun ac19_noActivity_dismisses_andReportsUnavailable() {
        val registry = TestActivityResultRegistry { _, _ -> null }.apply { failure = ActivityNotFoundException() }
        run(chooser(listOf("image/*")), registry)
        assertEquals(listOf<EngineResponse>(EngineResponse.Dismiss()), responses)
        assertEquals(1, unavailable)
    }
}
