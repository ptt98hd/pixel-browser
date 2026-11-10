package app.pixel.browser.feature.browser

import android.app.DownloadManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_CANCEL
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_PRIVATE_WARNING
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDownloadManager

/** Spec 016 AC14 – cảnh báo "tệp tải về vẫn ở trên thiết bị" khi tải từ tab ẩn danh. */
@RunWith(RobolectricTestRunner::class)
class DownloadDialogPrivateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var f: BrowserFixture
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val url = "https://a.com/files/f.pdf"
    private val mime = "application/pdf"

    @Before
    fun resetDownloads() {
        ShadowDownloadManager.reset()
    }

    private fun launch(private: Boolean) {
        f = BrowserFixture()
        if (private) f.viewModel.onNewPrivateTab()
        f.selected().visit("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulateDownload(url, mime, null, 2048) }
        rule.waitForIdle()
    }

    private val downloads get() = shadowOf(context.getSystemService(DownloadManager::class.java))

    @Test
    fun ac14_privateTab_showsWarning() {
        launch(private = true)
        assertTrue("download must come from a private tab", f.state().selectedIsPrivate)
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertExists()
        rule.onNodeWithTag(DOWNLOAD_PRIVATE_WARNING).assertExists()
    }

    @Test
    fun ac14_normalTab_showsNoWarning() {
        launch(private = false)
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertExists()
        rule.onNodeWithTag(DOWNLOAD_PRIVATE_WARNING).assertDoesNotExist()
    }

    @Test
    fun ac14_privateTab_startStillEnqueuesSystemDownload() {
        launch(private = true)
        rule.onNodeWithTag(DOWNLOAD_START).performClick()
        rule.waitForIdle()
        assertEquals(1, downloads.requestCount)
        assertEquals(EngineResponse.Dismiss(), f.selected().fake.responses[1L])
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac14_privateTab_cancelDismissesWithoutDownload() {
        launch(private = true)
        rule.onNodeWithTag(DOWNLOAD_CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(0, downloads.requestCount)
        assertEquals(EngineResponse.Dismiss(), f.selected().fake.responses[1L])
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertDoesNotExist()
    }
}
