package app.pixel.browser.feature.home

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.app.ActivityOptionsCompat
import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Registry ghi lại các lần launch thay cho Photo Picker thật. */
class TestActivityResultRegistry : ActivityResultRegistry() {
    data class Launch(
        val requestCode: Int,
        val contract: ActivityResultContract<*, *>,
        val input: Any?,
    )

    val launches = mutableListOf<Launch>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches += Launch(requestCode, contract, input)
    }

    fun <O> respond(result: O?) {
        val last = launches.last()
        dispatchResult(last.requestCode, result)
    }
}

/** Spec 010 – AC23: sheet tùy chỉnh nền. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeCustomizeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val registry = TestActivityResultRegistry()
    private val backgrounds = mutableListOf<HomeBackground>()
    private val dims = mutableListOf<Float>()
    private val picked = mutableListOf<String?>()

    private fun show(bg: HomeBackground) {
        val config = HomeConfig(shortcuts = shortcuts(2), background = bg)
        val actions =
            HomeActions(
                onSetBackground = { backgrounds += it },
                onSetDim = { dims += it },
                onImagePicked = { picked += it },
            )
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme { HomePage(config, actions, Modifier.fillMaxSize()) }
            }
        }
        rule.waitForIdle()
    }

    private fun openSheet() {
        rule.onNodeWithTag(HomeTestTags.CUSTOMIZE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.CUSTOMIZE_SHEET).assertExists()
    }

    @Test
    fun ac23_sheet_listsDynamicEightColorsAndImage_dynamicSelected() {
        show(HomeBackground.Dynamic)
        openSheet()
        rule.onNodeWithTag(HomeTestTags.BG_DYNAMIC).assertIsSelected()
        (0 until 8).forEach { rule.onNodeWithTag(HomeTestTags.bgColor(it)).assertExists().assertIsNotSelected() }
        rule.onNodeWithTag(HomeTestTags.bgColor(8)).assertDoesNotExist()
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE).assertExists()
    }

    @Test
    fun ac23_currentSolidColor_isSelected() {
        show(HomeBackground.Solid(HomeDefaults.colors[5]))
        openSheet()
        rule.onNodeWithTag(HomeTestTags.bgColor(5)).assertIsSelected()
        rule.onNodeWithTag(HomeTestTags.BG_DYNAMIC).assertIsNotSelected()
    }

    @Test
    fun ac23_currentImage_isSelected() {
        show(HomeBackground.Image("bg-1.jpg", 0.3f))
        openSheet()
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE).assertIsSelected()
    }

    @Test
    fun ac23_tapColor_setsSolidBackground() {
        show(HomeBackground.Dynamic)
        openSheet()
        rule.onNodeWithTag(HomeTestTags.bgColor(2)).performClick()
        rule.waitForIdle()
        assertEquals(listOf<HomeBackground>(HomeBackground.Solid(HomeDefaults.colors[2])), backgrounds)
    }

    @Test
    fun ac23_tapDynamic_setsDynamic() {
        show(HomeBackground.Solid(HomeDefaults.colors[0]))
        openSheet()
        rule.onNodeWithTag(HomeTestTags.BG_DYNAMIC).performClick()
        rule.waitForIdle()
        assertEquals(listOf<HomeBackground>(HomeBackground.Dynamic), backgrounds)
    }

    @Test
    fun ac23_tapImage_launchesImageOnlyPhotoPicker_andForwardsUri() {
        show(HomeBackground.Dynamic)
        openSheet()
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE).performClick()
        rule.waitForIdle()
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.PickVisualMedia)
        val request = launch.input as PickVisualMediaRequest
        assertEquals(ActivityResultContracts.PickVisualMedia.ImageOnly, request.mediaType)

        rule.runOnIdle { registry.respond(Uri.parse("content://media/picker/1")) }
        rule.waitForIdle()
        assertEquals(listOf<String?>("content://media/picker/1"), picked)
    }

    @Test
    fun ac23_pickerCancelled_forwardsNull() {
        show(HomeBackground.Dynamic)
        openSheet()
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE).performClick()
        rule.waitForIdle()
        rule.runOnIdle { registry.respond<Uri>(null) }
        rule.waitForIdle()
        assertEquals(listOf<String?>(null), picked)
    }

    @Test
    fun ac23_dimSlider_onlyForImage() {
        show(HomeBackground.Dynamic)
        openSheet()
        rule.onNodeWithTag(HomeTestTags.BG_DIM).assertDoesNotExist()
    }

    @Test
    fun ac23_dimSlider_rangeZeroToPointEight_andSetsDim() {
        show(HomeBackground.Image("bg-1.jpg", 0.3f))
        openSheet()
        val node = rule.onNodeWithTag(HomeTestTags.BG_DIM)
        val range =
            node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f, range.range.start, 0.0001f)
        assertEquals(0.8f, range.range.endInclusive, 0.0001f)
        assertEquals(0.3f, range.current, 0.0001f)
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        rule.waitForIdle()
        assertTrue(dims.isNotEmpty())
        assertEquals(0.5f, dims.last(), 0.01f)
    }
}
