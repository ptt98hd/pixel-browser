package app.pixel.browser.feature.browser.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 009 AC6 – `thumbnailSize` (JVM thuần). */
class ThumbnailSizeTest {
    @Test
    fun ac6_maxWidthConstant_is360() {
        assertEquals(360, THUMBNAIL_MAX_WIDTH)
    }

    @Test
    fun ac6_largeView_isScaledDownToMaxWidth_roundingToNearest() {
        assertEquals(ThumbnailSize(360, 667), thumbnailSize(1080, 2000))
    }

    @Test
    fun ac6_smallView_isNotScaledUp() {
        assertEquals(ThumbnailSize(300, 600), thumbnailSize(300, 600))
    }

    @Test
    fun ac6_zeroWidth_orZeroHeight_isNull() {
        assertNull(thumbnailSize(0, 100))
        assertNull(thumbnailSize(100, 0))
    }

    @Test
    fun ac6_customMaxWidth_isHonoured() {
        assertEquals(ThumbnailSize(540, 1000), thumbnailSize(1080, 2000, maxWidth = 540))
    }
}
