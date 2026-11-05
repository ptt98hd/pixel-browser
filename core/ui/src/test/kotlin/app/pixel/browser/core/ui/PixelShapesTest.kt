package app.pixel.browser.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 011 AC3. */
class PixelShapesTest {
    @Test
    fun ac3_shapes_followCornerScale4_8_16_24_32() {
        assertEquals(RoundedCornerShape(4.dp), PixelShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), PixelShapes.small)
        assertEquals(RoundedCornerShape(16.dp), PixelShapes.medium)
        assertEquals(RoundedCornerShape(24.dp), PixelShapes.large)
        assertEquals(RoundedCornerShape(32.dp), PixelShapes.extraLarge)
    }

    @Test
    fun ac3_typography_isDefaultM3() {
        assertEquals(Typography(), PixelTypography)
    }
}
