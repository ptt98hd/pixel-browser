package app.pixel.browser

import android.content.Intent
import android.content.MutableContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.engine.WebViewEngineSession
import app.pixel.browser.core.history.HistoryClearer
import app.pixel.browser.core.tabs.TabManager
import app.pixel.browser.core.ui.PixelTheme
import app.pixel.browser.core.ui.resolveDarkTheme
import app.pixel.browser.feature.browser.BrowserRoute
import app.pixel.browser.feature.browser.BrowserViewModel
import app.pixel.browser.feature.browser.intent.AndroidExternalAppLauncher
import app.pixel.browser.feature.browser.intent.IncomingIntent
import app.pixel.browser.feature.browser.intent.IncomingIntentResolver
import app.pixel.browser.feature.browser.intent.from
import app.pixel.browser.feature.browser.web.AndroidRuntimePermissionGate
import app.pixel.browser.feature.history.HistoryRoute
import app.pixel.browser.feature.history.HistoryViewModel
import app.pixel.browser.feature.home.AndroidBackgroundImageImporter
import app.pixel.browser.feature.home.HomeRoute
import app.pixel.browser.feature.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
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
                    // Gate giữ application context; Activity chỉ gắn launcher xin quyền (xem onCreate).
                    runtimePermissions = AndroidRuntimePermissionGate(appContext),
                    thumbnails = appContext.thumbnails,
                    suggestionProvider = appContext.suggestionProvider,
                    pageVisits = appContext.historyRecorder,
                )
            }
        }
    }

    /** Launcher xin quyền Android cho trang web (spec 007 AC15); kết quả về gate trong ViewModel. */
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            (viewModel.runtimePermissions as? AndroidRuntimePermissionGate)?.onResult(result)
        }

    /** Một `HomeViewModel` cho mọi tab: sửa trang chủ ở tab này thấy ngay ở tab khác (spec 010). */
    private val homeViewModel: HomeViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                HomeViewModel(
                    appContext.homeStore,
                    AndroidBackgroundImageImporter(appContext),
                    Dispatchers.IO,
                    // Scope cấp process: ghi cấu hình đang chờ không mất khi Activity bị hủy.
                    writeScope = appContext.ioScope,
                )
            }
        }
    }

    /** Màn hình Lịch sử (lớp phủ); ViewModel cấp Activity nên giữ truy vấn khi xoay (spec 012). */
    private val historyViewModel: HistoryViewModel by viewModels {
        viewModelFactory {
            initializer {
                val appContext = app
                HistoryViewModel(
                    appContext.historyStore,
                    HistoryClearer(appContext.historyStore, System::currentTimeMillis),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Bottom bar luôn có nền surfaceContainer sau inset nên không cần lớp scrim của hệ thống (spec 011 AC13).
        window.isNavigationBarContrastEnforced = false
        (viewModel.runtimePermissions as? AndroidRuntimePermissionGate)?.attach(permissionLauncher)
        setContent {
            val settings by app.themeSettings.collectAsState()
            PixelTheme(settings) {
                SystemBarsEffect(dark = resolveDarkTheme(settings.mode, isSystemInDarkTheme()))
                // Điều hướng cấp app: màn hình phụ là lớp phủ trên BrowserRoute (spec 012), sống qua xoay màn hình.
                var destination by rememberSaveable { mutableStateOf(AppDestination.Browser) }
                BrowserRoute(
                    viewModel,
                    // Nội dung dưới lớp phủ không được TalkBack đọc/focus.
                    modifier =
                        if (destination != AppDestination.Browser) {
                            Modifier.semantics { hideFromAccessibility() }
                        } else {
                            Modifier
                        },
                    overlayOpen = destination != AppDestination.Browser,
                    onOpenHistory = { destination = AppDestination.History },
                    onReturnToCaller = { moveTaskToBack(true) },
                    themeSettings = settings,
                    thumbnails = app.thumbnails,
                    onThemeChange = ::onThemeChange,
                    homeContent = {
                        HomeRoute(
                            homeViewModel,
                            onOpenUrl = viewModel::onOpenUrl,
                            onSearchClick = viewModel::onStartEditing,
                        )
                    },
                )
                if (destination == AppDestination.History) {
                    HistoryRoute(
                        historyViewModel,
                        onOpenUrl = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrl(it)
                        },
                        onOpenInNewTab = {
                            destination = AppDestination.Browser
                            viewModel.onOpenUrlInNewTab(it)
                        },
                        onClose = { destination = AppDestination.Browser },
                    )
                }
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

    override fun onDestroy() {
        (viewModel.runtimePermissions as? AndroidRuntimePermissionGate)?.detach(permissionLauncher)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // Chụp snapshot (saveState WebView) trên main, ghi đĩa trên IO.
        val snapshot = viewModel.tabsSnapshot()
        val store = app.tabStore
        app.ioScope.launch { store.write(snapshot) }
    }
}
