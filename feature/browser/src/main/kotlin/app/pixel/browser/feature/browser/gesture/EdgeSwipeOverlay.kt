package app.pixel.browser.feature.browser.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pixel.browser.feature.browser.BrowserTestTags
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Vuốt cạnh khi không có điều hướng cử chỉ (spec 008 AC9–AC11): vuốt vào trong từ mép 20dp để back
 * ([edgeBack]) / forward ([edgeForward]). Dải chỉ tồn tại khi cờ tương ứng bật. Trong RTL, back nằm ở mép phải.
 *
 * Cử chỉ được quan sát ở `PointerEventPass.Initial` trên vùng bọc [content] và chỉ consume sau khi vượt touch slop,
 * nên chạm (tap) ở mép vẫn tới trang. Hai node `edge_swipe_*` chỉ là marker hiển thị/semantics cho dải 20dp.
 * Hình học dùng hướng vật lý (Ltr) cho dải và indicator vì vị trí/hướng vuốt là vật lý.
 */
@Composable
fun EdgeSwipeOverlay(
    edgeBack: Boolean,
    edgeForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val leftEnabled = if (rtl) edgeForward else edgeBack
    val rightEnabled = if (rtl) edgeBack else edgeForward
    val onLeft = rememberUpdatedState(if (rtl) onForward else onBack)
    val onRight = rememberUpdatedState(if (rtl) onBack else onForward)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var progress by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    var fromLeft by remember { mutableStateOf(true) }
    val showIndicator by remember { derivedStateOf { visible } }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(leftEnabled, rightEnabled, density) {
                val tracker =
                    EdgeSwipeTracker(
                        thresholdPx = GestureConstants.EDGE_THRESHOLD.toPx(),
                        flingVelocityPx = GestureConstants.EDGE_FLING_VELOCITY * density.density,
                    )
                val edgePx = GestureConstants.EDGE_WIDTH.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val inward =
                        when {
                            leftEnabled && down.position.x <= edgePx -> 1f
                            rightEnabled && down.position.x >= size.width - edgePx -> -1f
                            else -> return@awaitEachGesture
                        }
                    tracker.reset()
                    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var last = down.position
                    var totalX = 0f
                    var totalY = 0f
                    var vertical = false
                    var dragging = false
                    var armed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val cancelled = !change.pressed && change.isConsumed
                            velocity.addPosition(change.uptimeMillis, change.position)
                            val delta = change.position - last
                            last = change.position
                            totalX += delta.x * inward
                            totalY += delta.y
                            val slop = viewConfiguration.touchSlop
                            if (!vertical && abs(totalY) > slop && abs(totalY) > abs(totalX) * 2) vertical = true
                            if (!vertical) tracker.onDrag(delta.x * inward)
                            if (!vertical && !dragging && totalX > slop) dragging = true
                            if (dragging && !vertical) {
                                change.consume()
                                fromLeft = inward > 0f
                                progress = tracker.progress
                                visible = true
                                if (tracker.isArmed && !armed) {
                                    armed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                            }
                            if (!change.pressed) {
                                if (!cancelled && dragging && !vertical &&
                                    tracker.onRelease(velocity.calculateVelocity().x * inward)
                                ) {
                                    (if (inward > 0f) onLeft else onRight).value()
                                }
                                break
                            }
                        }
                    } finally {
                        visible = false
                        progress = 0f
                    }
                }
            },
    ) {
        content()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.matchParentSize()) {
                if (leftEnabled) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(GestureConstants.EDGE_WIDTH)
                            .testTag(BrowserTestTags.EDGE_SWIPE_LEFT),
                    )
                }
                if (rightEnabled) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(GestureConstants.EDGE_WIDTH)
                            .testTag(BrowserTestTags.EDGE_SWIPE_RIGHT),
                    )
                }
                if (showIndicator) {
                    EdgeSwipeIndicator(progress = { progress }, fromLeft = fromLeft, isBack = fromLeft == !rtl)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.EdgeSwipeIndicator(
    progress: () -> Float,
    fromLeft: Boolean,
    isBack: Boolean,
) {
    val density = LocalDensity.current
    val travel = with(density) { TRAVEL.toPx() }
    val hidden = with(density) { SIZE.toPx() }
    val icon = if (isBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .align(if (fromLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .offset {
                    val p = progress()
                    val x = (p * travel - hidden * (1f - p)).roundToInt()
                    IntOffset(if (fromLeft) x else -x, 0)
                }.size(SIZE)
                .testTag(BrowserTestTags.EDGE_SWIPE_INDICATOR),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
    }
}

private val SIZE = 40.dp
private val TRAVEL = 56.dp
