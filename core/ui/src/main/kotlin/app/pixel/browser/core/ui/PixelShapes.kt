package app.pixel.browser.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

/** extraSmall 4, small 8, medium 16, large 24, extraLarge 32 (dp) – spec 011 AC3. */
val PixelShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Typography mặc định M3 với font hệ thống – spec 011 AC3. */
val PixelTypography: Typography = Typography()
