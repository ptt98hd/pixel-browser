package app.pixel.browser

import android.app.SearchManager
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import app.pixel.browser.core.data.search.FakeSearchSettingsStore
import app.pixel.browser.core.data.search.SearchSettings
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Spec 014 AC10 (phần `:app`) – intent tìm kiếm từ app khác dùng engine hiện tại. */
@RunWith(RobolectricTestRunner::class)
class MainActivitySearchIntentTest {
    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication

    private fun searchIntent(query: String) =
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).setClass(app, MainActivity::class.java)

    @Test
    fun ac10_webSearchIntent_usesStoredEngine() {
        app.bindSearchStore(
            FakeSearchSettingsStore(SearchSettings(selectedId = "duckduckgo")),
            CoroutineScope(Dispatchers.Unconfined),
        )
        val c = Robolectric.buildActivity(MainActivity::class.java, searchIntent("kotlin")).setup()
        val vm = ViewModelProvider(c.get())[BrowserViewModel::class.java]
        val session = vm.selectedSession.value as WebViewEngineSession
        assertEquals("https://duckduckgo.com/?q=kotlin", shadowOf(session.webView).lastLoadedUrl)
    }

    @Test
    fun ac10_onNewIntent_afterEngineChange_usesNewEngine() {
        app.bindSearchStore(FakeSearchSettingsStore(), CoroutineScope(Dispatchers.Unconfined))
        val c =
            Robolectric
                .buildActivity(
                    MainActivity::class.java,
                    Intent(Intent.ACTION_MAIN).setClass(app, MainActivity::class.java),
                ).setup()
        app.updateSearch { it.copy(selectedId = "bing") }
        c.newIntent(searchIntent("kotlin"))
        val vm = ViewModelProvider(c.get())[BrowserViewModel::class.java]
        val session = vm.selectedSession.value as WebViewEngineSession
        assertEquals("https://www.bing.com/search?q=kotlin", shadowOf(session.webView).lastLoadedUrl)
    }
}
