package app.pixel.browser

import android.app.Application
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.datastore.preferences.preferencesDataStoreFile
import app.pixel.browser.core.data.DataStoreThemeSettingsStore
import app.pixel.browser.core.data.ThemeSettings
import app.pixel.browser.core.data.ThemeSettingsStore
import app.pixel.browser.core.data.createSettingsDataStore
import app.pixel.browser.core.data.home.FileHomeConfigStore
import app.pixel.browser.core.data.home.HomeConfigStore
import app.pixel.browser.core.data.home.HomeFiles
import app.pixel.browser.core.tabs.FileTabSnapshotStore
import app.pixel.browser.core.tabs.TabSnapshotStore
import app.pixel.browser.core.ui.NightModeController
import app.pixel.browser.core.ui.UiModeNightModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** Phụ thuộc cấp process (DI thủ công tới khi có Hilt). */
class PixelBrowserApplication : Application() {
    /** `tabs.json` trong `noBackupFilesDir`: không bị Auto Backup/chuyển thiết bị sao lưu (spec 002). */
    val tabStore: TabSnapshotStore by lazy { FileTabSnapshotStore(File(noBackupFilesDir, "tabs.json")) }

    /** `filesDir/home/config.json` (spec 010); ảnh nền cùng thư mục. Không nằm trong backup (app tắt backup). */
    val homeStore: HomeConfigStore by lazy { FileHomeConfigStore(HomeFiles.config(filesDir)) }

    /**
     * Scope ghi đĩa sống theo process (không bị hủy cùng Activity/ViewModel). Tuần tự 1 luồng để hai lần
     * ghi `tabs.json` không chồng nhau.
     */
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val themeLock = Any()
    private var themeBinding: ThemeBinding? = null

    /** Số lần ghi theme đang chờ: trong lúc đó bỏ qua giá trị phát từ store (cũ hơn giá trị lạc quan). */
    private var pendingThemeWrites = 0
    private val _themeSettings = MutableStateFlow(ThemeSettings())

    /** Store cài đặt theme đang dùng (mặc định DataStore `settings`, spec 011). */
    val themeStore: ThemeSettingsStore
        get() = synchronized(themeLock) { checkNotNull(themeBinding) { "bindThemeStore chưa được gọi" }.store }

    /** Cài đặt theme hiện tại: bắt đầu `ThemeSettings()`, cập nhật từ [themeStore] (spec 011 AC14). */
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    /** Áp night mode khi người dùng đổi Sáng/Tối (spec 011 AC12). Test thay bằng bản ghi lại. */
    lateinit var nightModeController: NightModeController

    override fun onCreate() {
        super.onCreate()
        nightModeController = UiModeNightModeController(this)
        // Một DataStore cho file `settings` trong cả process (DataStore cấm hai instance cùng file).
        val settingsDataStore =
            createSettingsDataStore(CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
                preferencesDataStoreFile(SETTINGS_DATASTORE)
            }
        bindThemeStore(DataStoreThemeSettingsStore(settingsDataStore), ioScope)
    }

    /**
     * Đổi theme lạc quan: [themeSettings] nhận giá trị mới ngay (gọi trên main), night mode chỉ áp khi Sáng/Tối
     * đổi, rồi ghi [themeStore] trên [ioScope] (sống qua Activity tạo lại). Lỗi `IOException` khi ghi được ghi log,
     * giá trị trong bộ nhớ giữ nguyên tới lần khởi động sau.
     */
    @MainThread
    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        val store: ThemeSettingsStore
        val previous: ThemeSettings
        val next: ThemeSettings
        synchronized(themeLock) {
            previous = _themeSettings.value
            next = transform(previous)
            if (next == previous) return
            store = themeStore
            pendingThemeWrites++
            _themeSettings.value = next
        }
        ioScope.launch {
            try {
                store.update { next }
            } catch (e: IOException) {
                Log.w(TAG, "Không ghi được cài đặt theme", e)
            } finally {
                synchronized(themeLock) { pendingThemeWrites-- }
            }
        }
        if (next.mode != previous.mode) nightModeController.apply(next.mode)
    }

    /**
     * Gắn [store] làm [themeStore] và collect nó vào [themeSettings] trên [scope]; hủy lần gắn trước (giá trị của
     * lần gắn cũ không được ghi đè lần mới). Lỗi `IOException` khi đọc → giữ giá trị hiện tại, không ném.
     * `onCreate` gọi với DataStore + [ioScope]; test gọi lại với Fake + test dispatcher (spec 011 AC14).
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun bindThemeStore(
        store: ThemeSettingsStore,
        scope: CoroutineScope,
    ) {
        synchronized(themeLock) {
            themeBinding?.job?.cancel()
            val binding = ThemeBinding(store)
            themeBinding = binding
            binding.job =
                scope.launch {
                    store.settings
                        .catch { e -> if (e !is IOException) throw e }
                        .collect { value ->
                            // Job cũ có thể còn phát một giá trị sau khi bị hủy: chỉ lần gắn hiện tại được ghi.
                            // Đang có lần ghi chờ thì giá trị lạc quan mới hơn giá trị store vừa phát.
                            synchronized(themeLock) {
                                if (themeBinding === binding && pendingThemeWrites == 0) _themeSettings.value = value
                            }
                        }
                }
        }
    }

    private class ThemeBinding(
        val store: ThemeSettingsStore,
    ) {
        var job: Job? = null
    }

    private companion object {
        /** `settings.preferences_pb` – dùng chung cho các cài đặt khác ở B14. */
        const val SETTINGS_DATASTORE = "settings"
        const val TAG = "PixelBrowserApp"
    }
}
