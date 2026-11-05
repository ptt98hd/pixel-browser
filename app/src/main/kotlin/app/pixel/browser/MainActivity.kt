package app.pixel.browser

import android.content.MutableContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app: PixelBrowserApplication get() = application as PixelBrowserApplication

    /**
     * Session (WebView) sống trong ViewModel nên qua được xoay màn hình. WebView tạo bằng
     * [MutableContextWrapper] của application context; `EngineContainer` trỏ base context sang Activity khi gắn.
     *
     * TabManager phải tạo trên main thread (tạo WebView) và constructor đọc `tabs.json` đồng bộ — xem spec 005
     * "Quyết định".
     */
    private val viewModel: BrowserViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                BrowserViewModel(
                    TabManager(
                        sessionFactory = { WebViewEngineSession(MutableContextWrapper(appContext)) },
                        store = appContext.tabStore,
                    ),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PixelBrowserTheme { BrowserRoute(viewModel) } }
    }

    override fun onStop() {
        super.onStop()
        // Chụp snapshot (saveState WebView) trên main, ghi đĩa trên IO.
        val snapshot = viewModel.tabsSnapshot()
        val store = app.tabStore
        app.ioScope.launch { store.write(snapshot) }
    }
}
