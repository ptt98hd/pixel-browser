package app.pixel.browser

import android.view.View
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import app.pixel.browser.core.bookmarks.InMemoryBookmarkRepository
import app.pixel.browser.core.data.privacy.FakePrivacySettingsStore
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import app.pixel.browser.core.engine.FakeProfileGateway
import app.pixel.browser.core.engine.PrivateSpace
import app.pixel.browser.core.engine.RetryScheduler
import app.pixel.browser.feature.browser.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.UUID

/**
 * Spec 016 AC25 – "mỏ vàng": đi qua mọi đường ghi (tab, lịch sử, thumbnail, desktop, tìm trong trang, xoay màn hình,
 * onStop, đóng tab) với tab ẩn danh mang token canary, rồi quét **mọi file** dưới `filesDir`, `noBackupFilesDir`,
 * `cacheDir`, `databases/`: không được chứa token (UTF-8, UTF-16LE/BE); `ShadowLog` cũng sạch. Chạy lại với tab thường
 * làm đối chứng (token phải có trong `tabs.json`) để chứng minh việc quét có tác dụng.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PrivateNoDiskTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = RuntimeEnvironment.getApplication() as PixelBrowserApplication
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Session Fake có view thật để `BitmapTabThumbnailRepository` chụp được. */
    private class ViewedFake(
        val fake: FakeEngineSession,
        override val view: View?,
    ) : EngineSession by fake

    private val token = "canary-" + UUID.randomUUID()
    private val queryToken = UUID.randomUUID().toString()
    private val url = "https://$token.test/secret?q=$queryToken"

    @Before
    fun bind() {
        // Lịch sử là Room thật (do Application.onCreate gắn) trên ioScope; dấu trang trong bộ nhớ.
        app.bindBookmarkRepository(InMemoryBookmarkRepository())
        app.bindPrivacyStore(FakePrivacySettingsStore(), CoroutineScope(Dispatchers.Unconfined))
        app.bindPrivateSpace(PrivateSpace(FakeProfileGateway(), scheduler = RetryScheduler { _, _ -> }))
        app.bindBaseSessionFactory { profile ->
            val v =
                View(app).apply {
                    measure(
                        View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, WIDTH, HEIGHT)
                }
            ViewedFake(FakeEngineSession(isPrivate = profile != null), v)
        }
    }

    @After
    fun closeScenario() {
        scenario?.close()
    }

    private fun drainIo() = runBlocking { app.ioScope.launch {}.join() }

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

    /** Đi hết các đường ghi với [url] ở tab ẩn danh hoặc tab thường; trả id tab đã đóng cuối cùng. */
    private fun exercise(private: Boolean) {
        val s = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        rule.waitForIdle()
        val vm = vm(s)
        onMain(s) { if (private) vm.onNewPrivateTab() }
        onMain(s) { vm.onOpenUrl(url) }
        val tabId = vm.uiState.value.selectedTabId!!
        val fake = (vm.selectedSession.value as ViewedFake).fake
        onMain(s) {
            fake.simulatePageFinished(url)
            fake.simulateTitle("Secret page $token")
        }
        onMain(s) {
            vm.onOpenFind()
            vm.onFindQueryChange(queryToken)
        }
        onMain(s) { vm.onCloseFind() }
        onMain(s) { vm.onToggleDesktopSite() }
        onMain(s) { vm.onOpenTabSwitcher() } // chụp thumbnail
        s.moveToState(Lifecycle.State.CREATED) // onStop → tabs.json
        drainIo()
        s.moveToState(Lifecycle.State.RESUMED)
        s.recreate() // xoay Activity
        rule.waitForIdle()
        if (!private) {
            // Đối chứng: tab thường phải để lại token trong tabs.json trước khi đóng.
            val tabsJson = File(app.noBackupFilesDir, "tabs.json")
            assertTrue("control: tabs.json must contain the token of a normal tab", tabsJson.readText().contains(token))
        }
        onMain(s) { vm.onCloseTab(tabId) }
        drainIo()
        Thread.sleep(IO_SETTLE_MS) // thumbnail repository ghi/xóa trên Dispatchers.IO riêng
    }

    private fun scanDirs(): List<File> =
        listOf(
            app.filesDir,
            app.noBackupFilesDir,
            app.cacheDir,
            File(app.applicationInfo.dataDir, "databases"),
        )

    /** Mọi file (kể cả `-wal`/`-shm`) chứa [needle] ở dạng UTF-8 hoặc UTF-16LE/BE. */
    private fun filesContaining(needle: String): List<File> {
        val patterns = listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE).map { needle.toByteArray(it) }
        return scanDirs()
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
            .filter { file ->
                val bytes = file.readBytes()
                patterns.any { p -> bytes.indexOf(p) >= 0 }
            }
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        if (pattern.isEmpty() || size < pattern.size) return -1
        outer@ for (i in 0..size - pattern.size) {
            for (j in pattern.indices) if (this[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun ac25_privateTab_leavesNoTokenOnDisk_noLogs() {
        exercise(private = true)

        val leaks = (filesContaining(token) + filesContaining(queryToken)).map { it.path }
        assertTrue("canary token found in: $leaks", leaks.isEmpty())
        val logs = ShadowLog.getLogs().filter { it.msg.contains(token) || it.msg.contains(queryToken) }
        assertTrue("canary token logged: ${logs.map { it.msg }}", logs.isEmpty())
        val thumbs = File(app.cacheDir, "thumbnails").listFiles().orEmpty().filter { it.name.endsWith(".webp") }
        assertTrue("private tab must not persist a thumbnail: $thumbs", thumbs.isEmpty())
        assertTrue(
            "private tab must not leave desktop.json entries",
            File(app.filesDir, "site/desktop.json").let { !it.exists() || !it.readText().contains(token) },
        )
    }

    @Test
    fun ac25_control_normalTab_leavesTokenInTabsJson_provingTheScanWorks() {
        exercise(private = false)
        val hits = filesContaining(token).map { it.name }
        assertTrue("scan must find the token written by a normal tab, got $hits", "tabs.json" in hits)
    }

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 600
        const val IO_SETTLE_MS = 300L
    }
}
