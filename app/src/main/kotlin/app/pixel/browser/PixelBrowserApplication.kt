package app.pixel.browser

import android.app.Application
import app.pixel.browser.core.tabs.FileTabSnapshotStore
import app.pixel.browser.core.tabs.TabSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Phụ thuộc cấp process (DI thủ công tới khi có Hilt). */
class PixelBrowserApplication : Application() {
    /** `tabs.json` trong `noBackupFilesDir`: không bị Auto Backup/chuyển thiết bị sao lưu (spec 002). */
    val tabStore: TabSnapshotStore by lazy { FileTabSnapshotStore(File(noBackupFilesDir, "tabs.json")) }

    /**
     * Scope ghi đĩa sống theo process (không bị hủy cùng Activity/ViewModel). Tuần tự 1 luồng để hai lần
     * ghi `tabs.json` không chồng nhau.
     */
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
}
