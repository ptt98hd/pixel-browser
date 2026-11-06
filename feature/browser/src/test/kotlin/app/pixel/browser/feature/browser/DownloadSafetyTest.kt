package app.pixel.browser.feature.browser

import android.app.DownloadManager
import android.content.Context
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_START
import app.pixel.browser.feature.browser.web.sanitizeDownloadFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDownloadManager

/** Review spec 007: download không gửi cookie (B03), có User-Agent; tên file được làm sạch; lỗi enqueue có thông báo. */
@RunWith(RobolectricTestRunner::class)
class DownloadSafetyTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var f: BrowserFixture

    @Before
    fun reset() {
        ShadowDownloadManager.reset()
    }

    private fun startDownload(url: String) {
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulateDownload(url, "application/pdf", null, 10) }
        rule.waitForIdle()
        rule.onNodeWithTag(DOWNLOAD_START).performClick()
        rule.waitForIdle()
    }

    private fun headers(): List<Pair<String, String>> {
        val dm = shadowOf(context.getSystemService(DownloadManager::class.java))
        val request = (0L..20L).firstNotNullOfOrNull { dm.getRequest(it) }
        assertNotNull(request)
        return shadowOf(request).requestHeaders.map { it.first to it.second }
    }

    @Test
    fun download_hasUserAgent_andNoCookieHeader() {
        CookieManager.getInstance().setCookie("https://a.com/", "sid=secret")
        startDownload("https://a.com/f.pdf")
        val h = headers()
        assertFalse(h.toString(), h.any { it.first.equals("Cookie", ignoreCase = true) })
        assertTrue(h.toString(), h.any { it.first.equals("User-Agent", ignoreCase = true) && it.second.isNotBlank() })
    }

    @Test
    fun sanitize_replacesSeparatorsAndControlChars() {
        assertEquals("a_b_c_d", sanitizeDownloadFileName("a/b\\c\u0001d"))
        assertEquals("report.pdf", sanitizeDownloadFileName("report.pdf"))
    }

    @Test
    fun sanitize_emptyOrDotsOnly_becomesDownload() {
        for (name in listOf("", "  ", ".", "..", "...")) {
            assertEquals("'$name'", "download", sanitizeDownloadFileName(name))
        }
    }

    @Test
    fun enqueueFailure_showsMessage() {
        f = BrowserFixture()
        f.viewModel.onDownloadFailed()
        assertEquals(BrowserMessages.DOWNLOAD_FAILED, f.state().message)
    }
}
