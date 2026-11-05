package app.pixel.browser

import android.content.Intent
import android.content.MutableContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.ui.PixelTheme
import app.pixel.browser.core.ui.resolveDarkTheme
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.browser.intent.AndroidExternalAppLauncher
import app.pixel.browser.feature.browser.intent.IncomingIntent
import app.pixel.browser.feature.browser.intent.IncomingIntentResolver
import app.pixel.browser.feature.browser.intent.from
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
                    // Application context: ViewModel sống lâu hơn Activity (launcher thêm FLAG_ACTIVITY_NEW_TASK).
                    externalAppLauncher = AndroidExternalAppLauncher(appContext),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Bottom bar luôn có nền surfaceContainer sau inset nên không cần lớp scrim của hệ thống (spec 011 AC13).
        window.isNavigationBarContrastEnforced = false
        setContent {
            val settings by app.themeSettings.collectAsState()
            PixelTheme(settings) {
                SystemBarsEffect(dark = resolveDarkTheme(settings.mode, isSystemInDarkTheme()))
                BrowserRoute(
                    viewModel,
                    onReturnToCaller = { moveTaskToBack(true) },
                    themeSettings = settings,
                    onThemeChange = ::onThemeChange,
                )
            }
        }
        // Spec 006 AC16: intent chỉ xử lý ở lần tạo đầu; recreate (xoay, process death) không xử lý lại.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Spec 006 AC15: intent từ app khác → tab mới (xem [IncomingIntentResolver]). */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val request = IncomingIntentResolver.resolve(IncomingIntent.from(intent), viewModel.searchEngine)
        viewModel.onIncomingIntent(request)
    }

    /** Đổi Sáng/Tối gây configuration change → Activity tạo lại; WebView nằm trong ViewModel nên giữ trang. */
    private fun onThemeChange(next: ThemeSettings) = app.updateTheme { next }

    /** Icon status/navigation bar tối trên nền sáng và ngược lại, theo theme đang hiển thị (spec 011 AC13). */
    @Composable
    private fun SystemBarsEffect(dark: Boolean) {
        val view = LocalView.current
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Chụp snapshot (saveState WebView) trên main, ghi đĩa trên IO.
        val snapshot = viewModel.tabsSnapshot()
        val store = app.tabStore
        app.ioScope.launch { store.write(snapshot) }
    }
}
