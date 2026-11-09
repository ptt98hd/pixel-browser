package app.pixel.browser.feature.browser.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Vuốt ngang để sang tab kề (spec 008 AC12). Quan sát ở `PointerEventPass.Initial`; khi nhận ra vuốt ngang
 * thì consume để nút bên dưới không nhận tap/long-press. [onSelect] nhận delta -1 (trước) hoặc +1 (sau); trong RTL
 * chiều đảo ngược. [index] < 0 hoặc [enabled] = false thì không làm gì.
 */
fun Modifier.tabSwipe(
    enabled: Boolean,
    index: Int,
    count: Int,
    isRtl: Boolean,
    onSelect: (Int) -> Unit,
): Modifier =
    if (!enabled || index < 0) {
        this
    } else {
        composed {
            val currentOnSelect by rememberUpdatedState(onSelect)
            pointerInput(index, count, isRtl) {
                observeTabSwipe(index, count, isRtl) { currentOnSelect(it) }
            }
        }
    }

private suspend fun PointerInputScope.observeTabSwipe(
    index: Int,
    count: Int,
    isRtl: Boolean,
    onSelect: (Int) -> Unit,
) {
    val threshold = GestureConstants.TAB_SWIPE_THRESHOLD.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var last = down.position
        var total = Offset.Zero
        var dragging = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val cancelled = !change.pressed && change.isConsumed
            total += change.position - last
            last = change.position
            if (!dragging && abs(total.x) > viewConfiguration.touchSlop && abs(total.x) > 2 * abs(total.y)) {
                dragging = true
            }
            if (dragging) change.consume()
            if (!change.pressed) {
                if (dragging && !cancelled) {
                    val dx = if (isRtl) -total.x else total.x
                    val target = TabSwipe.target(dx, total.y, threshold, index, count)
                    if (target != null) onSelect(target - index)
                }
                break
            }
        }
    }
}
