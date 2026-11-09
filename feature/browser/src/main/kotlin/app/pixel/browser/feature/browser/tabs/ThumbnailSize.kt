package app.pixel.browser.feature.browser.tabs

/** Chiều rộng tối đa của thumbnail (px). */
const val THUMBNAIL_MAX_WIDTH = 360

data class ThumbnailSize(
    val width: Int,
    val height: Int,
)

/**
 * Kích thước thumbnail cho view [viewWidth]×[viewHeight]: thu nhỏ theo tỉ lệ về tối đa [maxWidth], không phóng to;
 * null nếu một chiều ≤ 0 (spec 009 AC6).
 */
fun thumbnailSize(
    viewWidth: Int,
    viewHeight: Int,
    maxWidth: Int = THUMBNAIL_MAX_WIDTH,
): ThumbnailSize? {
    if (viewWidth <= 0 || viewHeight <= 0) return null
    if (viewWidth <= maxWidth) return ThumbnailSize(viewWidth, viewHeight)
    val height = (viewHeight.toLong() * maxWidth + viewWidth / 2) / viewWidth
    return ThumbnailSize(maxWidth, height.toInt().coerceAtLeast(1))
}

/** Số cột của lưới tab theo chiều rộng [widthDp] (spec 009 AC7). */
fun tabGridColumns(widthDp: Int): Int =
    when {
        widthDp < MEDIUM_WIDTH_DP -> 2
        widthDp < EXPANDED_WIDTH_DP -> 3
        else -> 4
    }

private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840
