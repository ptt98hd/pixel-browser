package app.pixel.browser.core.ui

import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 011 AC2 – token lò xo M3 Expressive. */
class PixelMotionTest {
    private fun assertSpring(
        name: String,
        spec: SpringSpec<Float>,
        dampingRatio: Float,
        stiffness: Float,
    ) {
        assertEquals("$name dampingRatio", dampingRatio, spec.dampingRatio, 0f)
        assertEquals("$name stiffness", stiffness, spec.stiffness, 0f)
    }

    @Test
    fun ac2_spatialFast_is0_6_800() = assertSpring("spatialFast", PixelMotion.spatialFast(), 0.6f, 800f)

    @Test
    fun ac2_spatial_is0_8_380() = assertSpring("spatial", PixelMotion.spatial(), 0.8f, 380f)

    @Test
    fun ac2_spatialSlow_is0_8_200() = assertSpring("spatialSlow", PixelMotion.spatialSlow(), 0.8f, 200f)

    @Test
    fun ac2_effectsFast_is1_0_3800() = assertSpring("effectsFast", PixelMotion.effectsFast(), 1.0f, 3800f)

    @Test
    fun ac2_effects_is1_0_1600() = assertSpring("effects", PixelMotion.effects(), 1.0f, 1600f)

    @Test
    fun ac2_effectsSlow_is1_0_800() = assertSpring("effectsSlow", PixelMotion.effectsSlow(), 1.0f, 800f)
}
