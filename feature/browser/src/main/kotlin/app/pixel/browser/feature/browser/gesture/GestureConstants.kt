package app.pixel.browser.feature.browser.gesture

import androidx.compose.ui.unit.dp

/** Hằng số cử chỉ (spec 008), đơn vị dp. */
internal object GestureConstants {
    val EDGE_WIDTH = 20.dp
    val EDGE_THRESHOLD = 96.dp

    /** dp/s. */
    const val EDGE_FLING_VELOCITY = 1000f
    val TAB_SWIPE_THRESHOLD = 72.dp
    val PULL_THRESHOLD = 80.dp
    val PULL_MAX = 128.dp
}
