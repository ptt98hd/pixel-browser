package app.pixel.browser.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec 010 – AC22: nền trang chủ (Dynamic / Solid / Image + lớp dim). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class HomeBackgroundTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var surface: Color = Color.Unspecified

    private fun show(bg: HomeBackground) {
        val config = HomeConfig(shortcuts = shortcuts(2), background = bg)
        rule.setContent {
            MaterialTheme {
                surface = MaterialTheme.colorScheme.surface
                HomePage(config, HomeActions(), Modifier.fillMaxSize())
            }
        }
        rule.waitForIdle()
    }

    /** Màu ở góc trên-trái của `home_page` (vùng chỉ có nền). */
    private fun cornerColor(): Int =
        rule
            .onNodeWithTag(HomeTestTags.HOME_PAGE)
            .captureToImage()
            .toPixelMap()[2, 2]
            .toArgb()

    @Test
    fun ac22_dynamic_usesSurfaceColor_withoutImageLayer() {
        show(HomeBackground.Dynamic)
        assertEquals(surface.toArgb(), cornerColor())
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE_LAYER, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(HomeTestTags.BG_DIM_LAYER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac22_solid_usesThatColor() {
        val c = 0xFF1E88E5.toInt()
        show(HomeBackground.Solid(c))
        assertEquals(c, cornerColor())
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE_LAYER, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun ac22_image_hasImageLayer_andDimLayerWithAlpha() {
        show(HomeBackground.Image("bg-1.jpg", 0.4f))
        rule.onNodeWithTag(HomeTestTags.BG_IMAGE_LAYER, useUnmergedTree = true).assertExists()
        val dim =
            rule
                .onNodeWithTag(HomeTestTags.BG_DIM_LAYER, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[HomeSemantics.DimAlpha]
        assertEquals(0.4f, dim, 0.0001f)
    }
}
