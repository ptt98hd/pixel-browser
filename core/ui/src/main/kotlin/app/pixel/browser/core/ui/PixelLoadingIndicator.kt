package app.pixel.browser.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

private const val MORPH_MILLIS = 650
private const val ROTATION_MILLIS = 4_700
private const val FULL_TURN = 360f
private const val SHAPE_SCALE = 0.4f

/** Hai hình biến đổi qua lại (tâm 0,0, bán kính 1): "cookie" 7 cánh ↔ ngũ giác bo tròn. */
private val indicatorMorph: Morph by lazy {
    Morph(
        start = RoundedPolygon.star(7, radius = 1f, innerRadius = 0.75f, rounding = CornerRounding(0.25f)),
        end = RoundedPolygon(5, radius = 1f, rounding = CornerRounding(0.4f)),
    )
}

/**
 * Chỉ báo tải vô định dạng hình biến đổi (graphics-shapes) vừa biến hình vừa xoay, mặc định 48dp (spec 011 AC10).
 * Với [LocalReducedMotion] thì hình đứng yên.
 */
@Composable
fun PixelLoadingIndicator(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val (progress, rotation) = indicatorAnimation(LocalReducedMotion.current)
    val path = remember { Path() }
    Canvas(
        modifier
            .size(48.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        path.rewind()
        indicatorMorph.toPath(progress.value, path.asAndroidPath())
        val radius = size.minDimension * SHAPE_SCALE
        translate(center.x, center.y) {
            rotate(rotation.value, pivot = Offset.Zero) {
                scale(radius, pivot = Offset.Zero) {
                    drawPath(path, color)
                }
            }
        }
    }
}

/** (tiến độ biến hình 0..1, góc xoay) – hằng số khi giảm chuyển động. */
@Composable
private fun indicatorAnimation(reducedMotion: Boolean): Pair<State<Float>, State<Float>> {
    if (reducedMotion) {
        val still = rememberUpdatedState(0f)
        return still to still
    }
    val transition = rememberInfiniteTransition(label = "PixelLoadingIndicator")
    val progress =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(MORPH_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "morph",
        )
    val rotation =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN,
            animationSpec = infiniteRepeatable(tween(ROTATION_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation",
        )
    return progress to rotation
}
