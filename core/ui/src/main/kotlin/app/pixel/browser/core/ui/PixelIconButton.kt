package app.pixel.browser.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Semantics riêng của Pixel cho test. */
object PixelSemantics {
    /** Bo góc hiện tại (% cạnh ngắn, làm tròn) của [PixelIconButton] (spec 011 AC8). */
    val CornerPercent: SemanticsPropertyKey<Int> = SemanticsPropertyKey("PixelCornerPercent")
}

private const val REST_CORNER_PERCENT = 50f
private const val PRESSED_CORNER_PERCENT = 30f
private const val MAX_CORNER_PERCENT = 50
private val MIN_TOUCH_TARGET = 48.dp

/** Shape theo % đã tạo sẵn (0..50) để animation không cấp phát mỗi frame. */
private val cornerShapes = Array(MAX_CORNER_PERCENT + 1) { RoundedCornerShape(it) }

/**
 * Nút icon đổi hình khi nhấn: bo 50% khi nghỉ, 30% khi nhấn, animate bằng `pixelSpring(PixelMotion.spatialFast())`.
 * [enabled] = false: tap không đổi hình và không gọi [onClick]; [onLongClick] vẫn chạy (spec 011 AC8, khớp spec 005).
 * [onLongClickLabel] là nhãn trợ năng của long-press.
 */
@Composable
fun PixelIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val cornerPercent by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_CORNER_PERCENT else REST_CORNER_PERCENT,
        animationSpec = pixelSpring(PixelMotion.spatialFast()),
        label = "PixelIconButtonCorner",
    )
    Box(
        modifier =
            modifier
                .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                .semantics {
                    this[PixelSemantics.CornerPercent] = cornerPercent.roundToInt()
                    // Tap bị vô hiệu nhưng long-press vẫn chạy nên không dùng `enabled` của combinedClickable.
                    if (!enabled) disabled()
                }.graphicsLayer {
                    // Đọc giá trị trong graphicsLayer: animation chỉ vẽ lại, không recompose.
                    shape = cornerShapes[cornerPercent.roundToInt().coerceIn(0, MAX_CORNER_PERCENT)]
                    clip = true
                }.combinedClickable(
                    interactionSource = source,
                    indication = if (enabled) ripple() else null,
                    role = Role.Button,
                    onClick = { if (enabled) onClick() },
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
