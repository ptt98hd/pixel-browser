package app.pixel.browser.feature.browser

import android.app.DownloadManager
import android.content.Context
import android.text.format.Formatter
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pixel.browser.core.engine.EngineResponse
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_CANCEL
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_DIALOG
import app.pixel.browser.feature.browser.BrowserTestTags.DOWNLOAD_START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDownloadManager

/**
 * Spec 007 AC24 + Quyết định 2026-11-05: hộp thoại tải xuống; "Tải xuống" giao cho `DownloadManager` hệ thống
 * (thay cho chooser "mở bằng app khác" trong AC24 gốc).
 */
@RunWith(RobolectricTestRunner::class)
class DownloadDialogTest {
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

    private fun launch(contentLength: Long = 2048) {
        f = BrowserFixture()
        f.selected().visit("https://a.com/")
        rule.setContent { BrowserRoute(viewModel = f.viewModel) }
        rule.waitForIdle()
        rule.runOnIdle { f.selected().fake.simulateDownload(url, mime, null, contentLength) }
        rule.waitForIdle()
    }

    private fun responses() = f.selected().fake.responses

    private val downloads get() = shadowOf(context.getSystemService(DownloadManager::class.java))

    private fun enqueued(): DownloadManager.Request? = (0L..20L).firstNotNullOfOrNull { downloads.getRequest(it) }

    @Test
    fun ac24_dialog_showsGuessedFileName_andShortSize() {
        launch()
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertExists()
        val name = URLUtil.guessFileName(url, null, mime)
        rule.inDialog(DOWNLOAD_DIALOG, hasText(name, substring = true)).assertExists()
        val size = Formatter.formatShortFileSize(context, 2048)
        rule.inDialog(DOWNLOAD_DIALOG, hasText(size, substring = true)).assertExists()
    }

    @Test
    fun ac24_unknownLength_showsNoSize() {
        launch(contentLength = -1)
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertExists()
        val sizeLike = Regex("""\d+([.,]\d+)?\s?[kKMGT]?B\b""")
        rule
            .inDialog(
                DOWNLOAD_DIALOG,
                SemanticsMatcher("has size text") { node ->
                    node.config
                        .getOrElse(
                            SemanticsProperties.Text,
                        ) { emptyList() }
                        .any { sizeLike.containsMatchIn(it.text) }
                },
            ).assertDoesNotExist()
    }

    @Test
    fun ac24_start_enqueuesSystemDownload_thenDismisses() {
        launch()
        rule.onNodeWithTag(DOWNLOAD_START).performClick()
        rule.waitForIdle()
        assertEquals(1, downloads.requestCount)
        val request = shadowOf(enqueued()!!)
        assertEquals(url, request.uri.toString())
        assertEquals(mime, request.mimeType?.toString())
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED, request.notificationVisibility)
        assertEquals(URLUtil.guessFileName(url, null, mime), request.destination?.lastPathSegment)
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac24_cancel_dismisses_withoutDownloadOrActivity() {
        launch()
        rule.onNodeWithTag(DOWNLOAD_CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        assertEquals(0, downloads.requestCount)
        assertNull(shadowOf(rule.activity).nextStartedActivity)
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertDoesNotExist()
    }

    @Test
    fun ac24_back_dismisses_withoutDownload() {
        launch()
        rule.onNodeWithTag(DOWNLOAD_DIALOG).assertExists()
        rule.pressSystemBack()
        assertNotNull(responses()[1L])
        assertEquals(EngineResponse.Dismiss(), responses()[1L])
        assertEquals(0, downloads.requestCount)
    }
}
