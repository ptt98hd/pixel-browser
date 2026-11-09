package app.pixel.browser

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.Bookmark
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.bookmarks.ROOT_ID
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.omnibox.SuggestionKind
import app.pixel.browser.feature.bookmarks.BookmarksTestTags
import app.pixel.browser.feature.browser.BrowserTestTags
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Spec 013 AC27, AC28 – điều hướng lớp phủ Dấu trang, mở URL, sửa từ snackbar, gợi ý end-to-end. */
@RunWith(RobolectricTestRunner::class)
class MainActivityBookmarksTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val repo = InMemoryBookmarkRepository()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val page = "https://example.org/page"

    @Before
    fun bind() {
        val app = RuntimeEnvironment.getApplication() as PixelBrowserApplication
        app.bindHistoryStore(InMemoryHistoryStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindBookmarkRepository(repo)
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also {
            scenario = it
            rule.waitForIdle()
        }

    private fun vm(s: ActivityScenario<MainActivity>): BrowserViewModel {
        lateinit var vm: BrowserViewModel
        s.onActivity { vm = ViewModelProvider(it)[BrowserViewModel::class.java] }
        return vm
    }

    private fun onMain(
        s: ActivityScenario<MainActivity>,
        block: () -> Unit,
    ) {
        s.onActivity { block() }
        rule.waitForIdle()
    }

    private fun openUrl(
        s: ActivityScenario<MainActivity>,
        url: String,
    ): WebViewEngineSession {
        val vm = vm(s)
        onMain(s) { vm.onOpenUrl(url) }
        return vm.selectedSession.value as WebViewEngineSession
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun openBookmarks() {
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_BOOKMARKS)
    }

    private fun seed(
        url: String,
        title: String,
    ): Bookmark =
        runBlocking {
            (repo.addBookmark(url, title, ROOT_ID) as app.pixel.browser.core.bookmarks.BookmarkResult.Ok).value
        }

    private fun editable(tag: String): String =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.EditableText) {
                androidx.compose.ui.text
                    .AnnotatedString("")
            }.text

    private fun resetPauseFlags(webView: android.webkit.WebView) {
        val shadow = shadowOf(webView)
        listOf("onPauseCalled", "onResumeCalled").forEach {
            org.robolectric.shadows.ShadowWebView::class.java
                .getDeclaredField(it)
                .apply { isAccessible = true }
                .setBoolean(shadow, false)
        }
    }

    // ---------- AC27 ----------

    @Test
    fun ac27_menuBookmarks_opensFullScreenOverlay() {
        launch()
        openBookmarks()
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertExists()
        val root = rule.onRoot().getBoundsInRoot()
        val screen = rule.onNodeWithTag(BookmarksTestTags.SCREEN).getBoundsInRoot()
        assertEquals(root.width, screen.width)
        assertEquals(root.height, screen.height)
    }

    @Test
    fun ac27_open_pausesSelectedSession_close_resumesIt() {
        val s = launch()
        val session = openUrl(s, page)
        resetPauseFlags(session.webView)
        openBookmarks()
        assertTrue("page must be paused", shadowOf(session.webView).wasOnPauseCalled())
        resetPauseFlags(session.webView)
        tap(BookmarksTestTags.CLOSE)
        assertTrue("page must resume", shadowOf(session.webView).wasOnResumeCalled())
    }

    @Test
    fun ac27_tapBookmark_closesScreen_andLoadsUrlInSelectedTab() {
        val s = launch()
        val b = seed("https://opened.example/x", "Opened")
        openBookmarks()
        tap(BookmarksTestTags.item(b.id))
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertDoesNotExist()
        assertEquals(1, vm(s).uiState.value.tabCount)
        assertEquals("https://opened.example/x", vm(s).uiState.value.url)
    }

    @Test
    fun ac27_menuOpenInNewTab_selectsNewTab_closesScreen() {
        val s = launch()
        val b = seed("https://opened.example/x", "Opened")
        openBookmarks()
        rule.onNodeWithTag(BookmarksTestTags.item(b.id)).performTouchInput { longClick() }
        rule.waitForIdle()
        tap(BookmarksTestTags.MENU_OPEN_NEW_TAB)
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertDoesNotExist()
        assertEquals(2, vm(s).uiState.value.tabCount)
        assertEquals("https://opened.example/x", vm(s).uiState.value.url)
    }

    @Test
    fun ac27_closeButtonAndSystemBack_closeOverlay_keepingBrowserState() {
        val s = launch()
        openUrl(s, page)
        val before = vm(s).uiState.value
        openBookmarks()
        tap(BookmarksTestTags.CLOSE)
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertDoesNotExist()
        assertEquals(before.url, vm(s).uiState.value.url)
        openBookmarks()
        s.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertDoesNotExist()
        assertEquals(before.url, vm(s).uiState.value.url)
        assertEquals(before.tabCount, vm(s).uiState.value.tabCount)
    }

    @Test
    fun ac27_rotation_keepsOverlayOpen() {
        val s = launch()
        openBookmarks()
        s.recreate()
        rule.waitForIdle()
        rule.onNodeWithTag(BookmarksTestTags.SCREEN).assertExists()
    }

    @Test
    fun ac27_snackbarEdit_opensEditRouteForThatBookmark() {
        val s = launch()
        val session = openUrl(s, page)
        onMain(s) { session.dispatchPageFinished(page) }
        tap(BrowserTestTags.BTN_MENU)
        tap(BrowserTestTags.MENU_BOOKMARK)
        rule.waitForIdle()
        val saved = runBlocking { repo.observeByUrl(page).first() }
        assertTrue("bookmark must be saved", saved != null)
        tap(BrowserTestTags.BOOKMARK_EDIT_ACTION)
        rule.onNodeWithTag(BookmarksTestTags.EDIT_DIALOG).assertExists()
        assertEquals(page, editable(BookmarksTestTags.EDIT_URL_FIELD))
    }

    @Test
    fun ac27_removingStar_afterDeletingFromBookmarksScreen() {
        val s = launch()
        val session = openUrl(s, page)
        onMain(s) { session.dispatchPageFinished(page) }
        val b = seed(page, "Saved")
        rule.waitForIdle()
        assertTrue(vm(s).uiState.value.isBookmarked)
        openBookmarks()
        rule.onNodeWithTag(BookmarksTestTags.item(b.id)).performTouchInput { longClick() }
        rule.waitForIdle()
        tap(BookmarksTestTags.MENU_DELETE)
        tap(BookmarksTestTags.CLOSE)
        assertTrue(!vm(s).uiState.value.isBookmarked)
    }

    // ---------- AC28 ----------

    @Test
    fun ac28_typingInOmnibox_showsBookmarkSuggestion_tapLoadsUrl() {
        val s = launch()
        seed("https://en.wikipedia.org/", "Wikipedia")
        onMain(s) { vm(s).onStartEditing() }
        rule.onNodeWithTag(BrowserTestTags.URL_FIELD).performTextInput("wiki")
        rule.waitForIdle()
        val suggestions = vm(s).uiState.value.suggestions
        val index = suggestions.indexOfFirst { it.kind == SuggestionKind.Bookmark }
        assertTrue("suggestions=$suggestions", index >= 0)
        assertEquals("Wikipedia", suggestions[index].title)
        tap(BrowserTestTags.suggestion(index))
        assertEquals("https://en.wikipedia.org/", vm(s).uiState.value.url)
    }
}
