package app.pixel.browser

import android.content.Context
import app.pixel.browser.feature.browser.web.createCaptureUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Spec 007 AC19 – URI đích cho ảnh/video chụp qua FileProvider của app (cần provider trong manifest :app). */
@RunWith(RobolectricTestRunner::class)
class CaptureUriTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun ac19_captureUri_isFileProviderUnderCaptures_backedByCacheDir() {
        val uri = createCaptureUri(context)
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertEquals("captures", uri.pathSegments.first())
        val captures = File(context.cacheDir, "captures")
        assertTrue("cacheDir/captures phải tồn tại", captures.isDirectory)
        assertTrue(File(captures, uri.lastPathSegment!!).exists() || captures.listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun ac19_eachCapture_getsDistinctUri() {
        val a = createCaptureUri(context)
        val b = createCaptureUri(context)
        assertTrue("$a vs $b", a != b)
    }
}
