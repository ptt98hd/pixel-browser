package app.pixel.browser.feature.browser.tabs

import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Spec 009 AC10–AC11 – `BitmapTabThumbnailRepository` (Robolectric native graphics, `io` = StandardTestDispatcher). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapTabThumbnailRepositoryTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val scheduler = TestCoroutineScheduler()
    private val io = StandardTestDispatcher(scheduler)
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private lateinit var dir: File

    private var hostRepo by mutableStateOf<BitmapTabThumbnailRepository?>(null)
    private var hostId by mutableStateOf("")
    private var observed: ImageBitmap? = null

    @Before
    fun setUp() {
        dir = tmp.newFolder("thumbnails")
        rule.setContent {
            val repo = hostRepo
            if (repo == null) {
                SideEffect { observed = null }
            } else {
                key(repo, hostId) {
                    val image = repo.thumbnail(hostId)
                    SideEffect { observed = image }
                }
            }
        }
    }

    private fun repo(d: File = dir) = BitmapTabThumbnailRepository(d, io)

    private fun settle() {
        repeat(5) {
            scheduler.advanceUntilIdle()
            rule.waitForIdle()
        }
    }

    /** Đọc `thumbnail(id)` của [repo] trong composition, cho `io` chạy hết. */
    private fun read(
        repo: BitmapTabThumbnailRepository,
        id: String,
    ): ImageBitmap? {
        rule.runOnIdle {
            hostRepo = repo
            hostId = id
        }
        settle()
        return observed
    }

    private fun solidView(
        color: Int = Color.RED,
        width: Int = 1080,
        height: Int = 2000,
    ): View =
        View(context).apply {
            setBackgroundColor(color)
            measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }

    private fun sessionWith(v: View?): EngineSession =
        object : EngineSession by FakeEngineSession() {
            override val view: View? = v
        }

    private fun file(id: String) = File(dir, "$id.webp")

    // --- AC10: capture ---

    @Test
    fun ac10_capture_producesScaledRedRgb565Thumbnail() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)

        val bmp = read(repo, "t1")!!.asAndroidBitmap()

        assertEquals(360, bmp.width)
        assertEquals(667, bmp.height)
        assertEquals(Color.RED, bmp.getPixel(bmp.width / 2, bmp.height / 2))
        assertEquals(Bitmap.Config.RGB_565, bmp.config)
    }

    @Test
    fun ac10_capture_persistTrue_writesWebpOnIo_notOnTheCallingThread() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)
        assertFalse("file must be written on io, not synchronously", file("t1").exists())

        scheduler.advanceUntilIdle()

        assertTrue(file("t1").exists())
        assertTrue(file("t1").length() > 0)
    }

    @Test
    fun ac10_capture_persistFalse_keepsMemoryOnly_neverWritesFile() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = false)
        settle()

        assertNotNull(read(repo, "t1"))
        settle()
        assertFalse(file("t1").exists())
        assertTrue(dir.listFiles().orEmpty().none { it.name.startsWith("t1") })
    }

    @Test
    fun ac10_capture_withNullView_producesNothing() {
        val repo = repo()
        repo.capture("t1", sessionWith(null), persist = true)
        assertNull(read(repo, "t1"))
        assertFalse(file("t1").exists())
    }

    @Test
    fun ac10_capture_withZeroSizedView_producesNothing() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView(width = 0, height = 0)), persist = true)
        assertNull(read(repo, "t1"))
        assertFalse(file("t1").exists())
    }

    @Test
    fun ac10_capture_smallView_isNotScaledUp() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView(width = 300, height = 600)), persist = false)
        val bmp = read(repo, "t1")!!.asAndroidBitmap()
        assertEquals(300, bmp.width)
        assertEquals(600, bmp.height)
    }

    @Test
    fun ac10_unknownTab_hasNoThumbnail() {
        assertNull(read(repo(), "nope"))
    }

    // --- AC11: vòng đời file ---

    @Test
    fun ac11_remove_deletesFile_butMemoryCopyStaysForUndo() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)
        scheduler.advanceUntilIdle()
        assertTrue(file("t1").exists())

        repo.remove("t1")
        scheduler.advanceUntilIdle()

        assertFalse(file("t1").exists())
        assertNotNull(read(repo, "t1"))
    }

    @Test
    fun ac11_newRepositoryOnSameDir_readsThumbnailFromDisk() {
        val first = repo()
        first.capture("t1", sessionWith(solidView()), persist = true)
        scheduler.advanceUntilIdle()

        val bmp = read(repo(), "t1")!!.asAndroidBitmap()

        assertEquals(360, bmp.width)
        assertEquals(667, bmp.height)
        val px = bmp.getPixel(bmp.width / 2, bmp.height / 2)
        // WebP lossy: gần đỏ.
        assertTrue(Color.red(px) > 200)
        assertTrue(Color.green(px) < 60)
        assertTrue(Color.blue(px) < 60)
    }

    @Test
    fun ac11_retainOnly_deletesOrphanWebp_keepsListed_ignoresOtherFiles() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)
        repo.capture("t2", sessionWith(solidView()), persist = true)
        scheduler.advanceUntilIdle()
        val note = File(dir, "notes.txt").apply { writeText("keep") }
        val tmpFile = File(dir, "t3.webp.tmp").apply { writeText("keep") }

        repo.retainOnly(setOf("t2"))
        scheduler.advanceUntilIdle()

        assertFalse(file("t1").exists())
        assertTrue(file("t2").exists())
        assertTrue(note.exists())
        // Review: file .tmp mồ côi cũng bị dọn.
        assertFalse(tmpFile.exists())
    }

    @Test
    fun ac11_trimBackgroundAndHigher_clearsMemory_keepsFiles() {
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        ).forEach { level ->
            val sub = tmp.newFolder("trim$level")
            val repo = BitmapTabThumbnailRepository(sub, io)
            repo.capture("mem", sessionWith(solidView()), persist = false)
            repo.capture("disk", sessionWith(solidView()), persist = true)
            scheduler.advanceUntilIdle()

            repo.trim(level)

            assertNull("memory-only thumbnail must be gone at level $level", read(repo, "mem"))
            assertTrue("file must stay at level $level", File(sub, "disk.webp").exists())
        }
    }

    @Test
    fun ac11_trimRunningLow_changesNothing() {
        val repo = repo()
        repo.capture("mem", sessionWith(solidView()), persist = false)

        repo.trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertNotNull(read(repo, "mem"))
    }

    @Test
    fun ac11_corruptFile_readsAsNull_andIsDeleted() {
        file("bad").writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        assertNull(read(repo(), "bad"))
        scheduler.advanceUntilIdle()

        assertFalse(file("bad").exists())
    }

    @Test
    fun ac11_cacheDirWipedBySystem_givesPlaceholderWithoutError() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)
        scheduler.advanceUntilIdle()
        dir.deleteRecursively()

        assertNull(read(repo(), "t1"))
        repo.remove("t1")
        repo.retainOnly(setOf("x"))
        scheduler.advanceUntilIdle()
    }

    @Test
    fun ac11_clearDisk_deletesAllWebpFiles() {
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)
        repo.capture("t2", sessionWith(solidView()), persist = true)
        scheduler.advanceUntilIdle()

        repo.clearDisk()
        scheduler.advanceUntilIdle()

        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".webp") })
    }

    // --- Review: file .tmp mồ côi, id không an toàn ---

    @Test
    fun retainOnly_deletesOrphanTmpFiles() {
        val orphan = File(dir, "t3.webp.tmp").apply { writeText("x") }
        val repo = repo()
        repo.retainOnly(setOf("t3"))
        scheduler.advanceUntilIdle()
        assertFalse(orphan.exists())
    }

    @Test
    fun clearDisk_deletesTmpFilesToo() {
        val orphan = File(dir, "t3.webp.tmp").apply { writeText("x") }
        repo().clearDisk()
        scheduler.advanceUntilIdle()
        assertFalse(orphan.exists())
    }

    @Test
    fun write_failure_leavesNoTmpFile() {
        // `t1.webp` là thư mục không rỗng → rename thất bại; tmp phải bị xóa.
        File(dir, "t1.webp").apply {
            mkdirs()
            File(this, "keep").writeText("x")
        }
        val repo = repo()
        repo.capture("t1", sessionWith(solidView()), persist = true)
        scheduler.advanceUntilIdle()
        assertFalse(File(dir, "t1.webp.tmp").exists())
    }

    @Test
    fun unsafeTabIds_neverTouchTheFileSystem() {
        val outside = File(dir.parentFile, "evil.webp")
        listOf("../evil", "a/b", "a\\b", "..").forEach { id ->
            val repo = repo()
            repo.capture(id, sessionWith(solidView()), persist = true)
            repo.remove(id)
            scheduler.advanceUntilIdle()
        }
        assertFalse(outside.exists())
        assertTrue(dir.listFiles().orEmpty().none { it.isFile })
    }
}
