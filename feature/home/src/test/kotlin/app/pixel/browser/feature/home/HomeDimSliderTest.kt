package app.pixel.browser.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Review spec 010: kéo slider chỉ ghi config một lần (khi thả), dim vẫn hiển thị trực tiếp khi kéo. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeDimSliderTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val dims = mutableListOf<Float>()

    @Test
    fun dragGesture_commitsDimOnce_onRelease() {
        val config = HomeConfig(shortcuts = shortcuts(2), background = HomeBackground.Image("bg-1.jpg", 0.3f))
        rule.setContent {
            MaterialTheme { HomePage(config, HomeActions(onSetDim = { dims += it }), Modifier.fillMaxSize()) }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTestTags.CUSTOMIZE).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(HomeTestTags.BG_DIM).performTouchInput {
            down(Offset(width * 0.3f, centerY))
            repeat(8) { moveBy(Offset(width * 0.06f, 0f), delayMillis = 16) }
            up()
        }
        rule.waitForIdle()

        assertEquals("one write per gesture: $dims", 1, dims.size)
        assertTrue(dims.single() > 0.3f)
    }
}
