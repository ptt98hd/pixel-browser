package app.pixel.browser.feature.browser.tabs

import android.app.Application
import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.pixel.browser.core.engine.EngineSession
import app.pixel.browser.core.engine.FakeEngineSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

/** Spec 016 – `TabThumbnailRepository.purge` xóa cả bộ nhớ lẫn đĩa của các tab chỉ định. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapTabThumbnailRepositoryPurgeTest {
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

    private fun settle() {
        repeat(5) {
            scheduler.advanceUntilIdle()
            rule.waitForIdle()
        }
    }

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

    private fun session(): EngineSession =
        object : EngineSession by FakeEngineSession() {
            override val view: View =
                View(context).apply {
                    setBackgroundColor(Color.RED)
                    measure(
                        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                    )
                    layout(0, 0, 300, 600)
                }
        }

    private fun file(id: String) = File(dir, "$id.webp")

    @Test
    fun purge_dropsMemoryCopy_ofPrivateThumbnail() {
        val repo = BitmapTabThumbnailRepository(dir, io)
        repo.capture("p1", session(), persist = false)
        assertNotNull(read(repo, "p1"))

        repo.purge(setOf("p1"))
        settle()

        assertNull(read(repo, "p1"))
    }

    @Test
    fun purge_deletesFileOnDisk_andMemoryCopy() {
        val repo = BitmapTabThumbnailRepository(dir, io)
        repo.capture("p1", session(), persist = true)
        settle()
        assertTrue(file("p1").exists())

        repo.purge(setOf("p1"))
        settle()

        assertFalse(file("p1").exists())
        assertNull(read(repo, "p1"))
    }

    @Test
    fun purge_leavesOtherTabsUntouched() {
        val repo = BitmapTabThumbnailRepository(dir, io)
        repo.capture("p1", session(), persist = false)
        repo.capture("n1", session(), persist = true)
        settle()

        repo.purge(setOf("p1"))
        settle()

        assertNotNull(read(repo, "n1"))
        assertTrue(file("n1").exists())
    }

    @Test
    fun purge_emptySetAndUnknownIds_areHarmless() {
        val repo = BitmapTabThumbnailRepository(dir, io)
        repo.capture("n1", session(), persist = true)
        settle()
        repo.purge(emptySet())
        repo.purge(setOf("nope", "../escape"))
        settle()
        assertNotNull(read(repo, "n1"))
    }
}
