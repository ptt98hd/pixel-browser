package app.pixel.browser.feature.browser.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.ui.PixelLoadingIndicator
import app.pixel.browser.feature.browser.BrowserTestTags
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Bọc [content] (vùng engine) bằng cử chỉ kéo-để-tải-lại (spec 008 AC15–AC17). Chỉ quan sát chạm ở
 * `PointerEventPass.Initial` (không consume) và dựa vào `EngineSession.topOverscroll`; [enabled] = false
 * (theo `GestureGate`) thì không quan sát gì. Thả quá ngưỡng thì gọi [onReload] và hiện indicator đang tải cho tới
 * khi session hết `isLoading`.
 */
@Composable
fun PullToRefreshHost(
    session: EngineSession?,
    enabled: Boolean,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentOnReload by rememberUpdatedState(onReload)
    val currentSession by rememberUpdatedState(session)
    val tracker =
        remember(density) {
            with(
                density,
            ) { PullToRefreshTracker(GestureConstants.PULL_THRESHOLD.toPx(), GestureConstants.PULL_MAX.toPx()) }
        }
    // distance chỉ được đọc trong pha layout/draw (không gây recomposition mỗi sự kiện chạm).
    var distance by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(session) {
        refreshJob?.cancel()
        refreshing = false
    }

    val observe =
        if (enabled && session != null) {
            Modifier.pointerInput(session, tracker) {
                val thresholdPx = GestureConstants.PULL_THRESHOLD.toPx()

                fun sync() {
                    distance = tracker.distancePx
                    if (active != tracker.isActive) active = tracker.isActive
                }

                fun startRefresh(target: EngineSession) {
                    refreshJob?.cancel()
                    refreshJob =
                        scope.launch {
                            // Chờ reload thật sự bắt đầu (có timeout) rồi chờ tải xong.
                            withTimeoutOrNull(LOADING_START_TIMEOUT_MS) { target.state.first { it.isLoading } }
                            target.state.first { !it.isLoading }
                            refreshing = false
                        }
                }
                coroutineScope {
                    launch {
                        session.topOverscroll.collect {
                            tracker.onTopOverscroll()
                            sync()
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        tracker.onDown(session.state.value.isAtTop)
                        sync()
                        var last = down.position
                        var finished = false
                        var fired = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    // Compose báo hủy cử chỉ bằng sự kiện nhả đã bị consume.
                                    if (change.isConsumed) break
                                    finished = true
                                    val reload = tracker.onUp()
                                    sync()
                                    if (reload && session === currentSession) {
                                        currentOnReload()
                                        refreshing = true
                                        startRefresh(session)
                                    }
                                    break
                                }
                                tracker.onMove(change.position.y - last.y)
                                last = change.position
                                sync()
                                if (!fired && distance >= thresholdPx) {
                                    fired = true
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                            }
                        } finally {
                            if (!finished) {
                                tracker.onCancel()
                                sync()
                            }
                        }
                    }
                }
            }
        } else {
            Modifier
        }
    Box(modifier.fillMaxSize().then(observe)) {
        content()
        if (active || refreshing) {
            PullRefreshIndicator(distancePx = { distance }, refreshing = refreshing)
        }
    }
}

/** Indicator ở đầu vùng nội dung: mũi tên xoay theo khoảng kéo, hoặc [PixelLoadingIndicator] khi [refreshing]. */
@Composable
fun PullRefreshIndicator(
    distancePx: () -> Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val size = 48.dp
    val sizePx = with(density) { size.toPx() }
    val restingPx = with(density) { GestureConstants.PULL_THRESHOLD.toPx() }
    Box(modifier.fillMaxSize()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val shown = if (refreshing) restingPx else distancePx()
                        IntOffset(0, (shown - sizePx).roundToInt())
                    }.size(size)
                    .testTag(BrowserTestTags.PULL_REFRESH_INDICATOR),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (refreshing) {
                    PixelLoadingIndicator()
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = distancePx() / restingPx * FULL_TURN_DEGREES },
                    )
                }
            }
        }
    }
}

private const val FULL_TURN_DEGREES = 270f
private const val LOADING_START_TIMEOUT_MS = 1_000L
