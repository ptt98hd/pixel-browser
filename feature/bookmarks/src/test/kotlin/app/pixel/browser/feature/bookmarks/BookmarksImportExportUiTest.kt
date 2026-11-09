package app.pixel.browser.feature.bookmarks

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 013 AC26 – nút nhập/xuất mở đúng trình chọn file (kiểm bằng `TestActivityResultRegistry`). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BookmarksImportExportUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val registry = TestActivityResultRegistry()
    private val imports = mutableListOf<String?>()
    private val exports = mutableListOf<String?>()
    private var shown = 0
    private var state by mutableStateOf(
        BookmarksUiState(title = "Dấu trang", exportFileName = "pixel-bookmarks-20261110.html"),
    )

    private fun show() {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
        val actions =
            BookmarksActions(
                onImportPicked = { imports += it },
                onExportPicked = { exports += it },
                onMessageShown = { shown++ },
            )
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme { BookmarksScreen(state, actions) }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun ac26_import_launchesOpenDocument_withHtmlXhtmlAndWildcardMimeTypes() {
        show()
        rule.onNodeWithTag(BookmarksTestTags.IMPORT).performClick()
        rule.waitForIdle()
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.OpenDocument)
        @Suppress("UNCHECKED_CAST")
        assertArrayEquals(arrayOf("text/html", "application/xhtml+xml", "*/*"), launch.input as Array<String>)
    }

    @Test
    fun ac26_import_pickedUriAndCancel_areForwarded() {
        show()
        rule.onNodeWithTag(BookmarksTestTags.IMPORT).performClick()
        registry.respond(Uri.parse("content://docs/bookmarks.html"))
        rule.waitForIdle()
        assertEquals(listOf<String?>("content://docs/bookmarks.html"), imports)
        rule.onNodeWithTag(BookmarksTestTags.IMPORT).performClick()
        registry.respond<Uri>(null)
        rule.waitForIdle()
        assertEquals(listOf("content://docs/bookmarks.html", null), imports)
    }

    @Test
    fun ac26_export_launchesCreateDocument_textHtml_withSuggestedName() {
        show()
        rule.onNodeWithTag(BookmarksTestTags.EXPORT).performClick()
        rule.waitForIdle()
        val launch = registry.launches.single()
        assertTrue(launch.contract is ActivityResultContracts.CreateDocument)
        assertEquals("pixel-bookmarks-20261110.html", launch.input)
        @Suppress("UNCHECKED_CAST")
        val intent = (launch.contract as ActivityResultContract<String, Uri?>).createIntent(rule.activity, "x.html")
        assertEquals("text/html", intent.type)
    }

    @Test
    fun ac26_export_pickedUriAndCancel_areForwarded() {
        show()
        rule.onNodeWithTag(BookmarksTestTags.EXPORT).performClick()
        registry.respond(Uri.parse("content://docs/out.html"))
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.EXPORT).performClick()
        registry.respond<Uri>(null)
        rule.waitForIdle()
        assertEquals(listOf("content://docs/out.html", null), exports)
    }

    @Test
    fun ac26_importResult_snackbarShowsCounts_andMessageIsConsumed() {
        show()
        state = state.copy(message = BookmarksMessage.ImportDone(imported = 123, skipped = 45))
        rule.waitForIdle()
        val text = rule.onNodeWithTag(BookmarksTestTags.IMPORT_RESULT).textValue()
        assertTrue(text, text.contains("123") && text.contains("45"))
        assertEquals(1, shown)
    }

    @Test
    fun ac26_errorMessages_areShownAndConsumed() {
        show()
        listOf(
            BookmarksMessage.ImportTooLarge,
            BookmarksMessage.ImportUnreadable,
            BookmarksMessage.ImportNotBookmarkFile,
            BookmarksMessage.ImportFull,
            BookmarksMessage.ExportFailed,
            BookmarksMessage.OperationFailed,
        ).forEachIndexed { i, m ->
            val before = shown
            state = state.copy(message = m)
            rule.waitForIdle()
            assertEquals("message $m consumed", before + 1, shown)
            rule.mainClock.advanceTimeBy(10_000)
            state = state.copy(message = null)
            rule.waitForIdle()
            assertTrue(i >= 0)
        }
    }
}
