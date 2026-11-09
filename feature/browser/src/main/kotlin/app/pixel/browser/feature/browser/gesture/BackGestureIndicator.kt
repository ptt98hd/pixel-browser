package app.pixel.browser.feature.browser.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.pixel.browser.feature.browser.BrowserTestTags

/**
 * Mũi tên báo trước của predictive back, ở cạnh vật lý bắt đầu cử chỉ và to dần theo [progress] 0..1
 * (spec 008 AC7). [progress] được đọc trong pha layout nên cập nhật không gây recomposition. `swipeEdge` là vị
 * trí vật lý nên hình học luôn dùng Ltr.
 */
@Composable
fun BackGestureIndicator(
    progress: () -> Float,
    fromRight: Boolean,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(if (fromRight) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 8.dp)
                    .layout { measurable, _ ->
                        val size = (MIN_SIZE + (MAX_SIZE - MIN_SIZE) * progress().coerceIn(0f, 1f)).roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(size, size))
                        layout(size, size) { placeable.place(0, 0) }
                    }.testTag(BrowserTestTags.BACK_GESTURE_INDICATOR),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            }
        }
    }
}

private val MIN_SIZE = 28.dp
private val MAX_SIZE = 48.dp
