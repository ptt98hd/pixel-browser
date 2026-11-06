package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.feature.browser.web.FileChooserLaunch
import app.pixel.browser.feature.browser.web.FileChooserMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 007 AC18 – ánh xạ `FileChooser` → cách mở trình chọn (JVM). */
class FileChooserMapperTest {
    private val ext = mapOf(".pdf" to "application/pdf")::get

    private fun map(
        accept: List<String>,
        multiple: Boolean = false,
        capture: Boolean = false,
    ) = FileChooserMapper.map(EngineRequest.FileChooser(1, "https://a.com", accept, multiple, capture), ext)

    @Test
    fun ac18_noAccept_single_isAnyType() {
        assertEquals(FileChooserLaunch.GetContent("*/*"), map(emptyList()))
    }

    @Test
    fun ac18_singleMime_isThatMime() {
        assertEquals(FileChooserLaunch.GetContent("image/*"), map(listOf("image/*")))
    }

    @Test
    fun ac18_knownExtension_isMapped() {
        assertEquals(FileChooserLaunch.GetContent("application/pdf"), map(listOf(".pdf")))
    }

    @Test
    fun ac18_severalTypes_single_isAnyType() {
        assertEquals(FileChooserLaunch.GetContent("*/*"), map(listOf("image/*", "video/*")))
    }

    @Test
    fun ac18_multiple_mapsEveryType() {
        assertEquals(
            FileChooserLaunch.OpenMultiple(listOf("image/*", "application/pdf")),
            map(listOf("image/*", ".pdf"), multiple = true),
        )
    }

    @Test
    fun ac18_multiple_noAccept_isAnyType() {
        assertEquals(FileChooserLaunch.OpenMultiple(listOf("*/*")), map(emptyList(), multiple = true))
    }

    @Test
    fun ac18_unknownExtension_isDropped() {
        assertEquals(FileChooserLaunch.GetContent("*/*"), map(listOf(".xyz")))
    }

    @Test
    fun ac18_captureImage_isTakePicture() {
        assertEquals(FileChooserLaunch.TakePicture, map(listOf("image/*"), capture = true))
    }

    @Test
    fun ac18_captureVideo_isCaptureVideo() {
        assertEquals(FileChooserLaunch.CaptureVideo, map(listOf("video/*"), capture = true))
    }

    @Test
    fun ac18_captureWithoutSingleMediaType_isLikeNoCapture() {
        assertEquals(FileChooserLaunch.GetContent("*/*"), map(emptyList(), capture = true))
        assertEquals(FileChooserLaunch.GetContent("*/*"), map(listOf("image/*", "video/*"), capture = true))
    }
}
