package app.pixel.browser.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/** Spec 010 – AC14: nhập ảnh nền (decode, thu nhỏ, JPEG) bằng graphics native của Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidBackgroundImageImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = RuntimeEnvironment.getApplication()
    private val homeDir: File get() = File(context.filesDir, "home")
    private val importer by lazy { AndroidBackgroundImageImporter(context) }

    private fun png(
        width: Int,
        height: Int,
        name: String = "holiday-photo.png",
    ): File {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.rgb(30, 136, 229))
        val f = tmp.newFile(name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    private fun uriOf(f: File) = Uri.fromFile(f).toString()

    private fun bounds(f: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return o.outWidth to o.outHeight
    }

    private fun homeFiles(): List<String> = homeDir.list()?.toList().orEmpty()

    @Test
    fun ac14_largePng_isDownscaledToJpeg_keepingAspectRatio() =
        runTest {
            val name = importer.import(uriOf(png(4000, 3000)))
            assertNotNull(name)
            val out = File(homeDir, name!!)
            assertTrue(out.exists())
            val header = out.readBytes().take(2)
            assertEquals(listOf(0xFF.toByte(), 0xD8.toByte()), header) // JPEG SOI
            val (w, h) = bounds(out)
            assertTrue("long edge $w must be <= 2048", maxOf(w, h) <= 2048)
            assertTrue("long edge should be scaled to the limit, was $w", w >= 2047)
            assertTrue("aspect ratio kept: ${w}x$h", abs(h - w * 3 / 4) <= 1)
        }

    @Test
    fun ac14_smallImage_keepsItsSize() =
        runTest {
            val name = importer.import(uriOf(png(800, 600)))
            assertNotNull(name)
            assertEquals(800 to 600, bounds(File(homeDir, name!!)))
        }

    @Test
    fun ac14_fileName_isBgNumberJpg_notDerivedFromSource() =
        runTest {
            val name = importer.import(uriOf(png(800, 600, name = "secret-name.png")))!!
            assertTrue(name, Regex("""bg-\d+\.jpg""").matches(name))
            assertFalse(name.contains("secret"))
            assertTrue(importer.exists(name))
        }

    @Test
    fun ac14_unreadableUri_returnsNull_withoutLeftovers() =
        runTest {
            assertNull(importer.import("content://does.not.exist/image/1"))
            assertNull(importer.import(Uri.fromFile(File(tmp.root, "missing.png")).toString()))
            assertTrue(homeFiles().toString(), homeFiles().isEmpty())
        }

    @Test
    fun ac14_notAnImage_returnsNull_withoutLeftovers() =
        runTest {
            val f = tmp.newFile("note.png").apply { writeText("this is not an image") }
            assertNull(importer.import(uriOf(f)))
            assertTrue(homeFiles().toString(), homeFiles().isEmpty())
        }

    @Test
    fun ac14_imageOver50MB_returnsNull_withoutLeftovers() =
        runTest {
            val f = png(800, 600, name = "huge.png")
            RandomAccessFile(f, "rw").use { it.setLength(50L * 1024 * 1024 + 1) }
            assertNull(importer.import(uriOf(f)))
            assertTrue(homeFiles().toString(), homeFiles().isEmpty())
        }

    @Test
    fun ac14_existsAndDelete_operateInHomeDir() =
        runTest {
            val name = importer.import(uriOf(png(100, 100)))!!
            assertTrue(importer.exists(name))
            importer.delete(name)
            assertFalse(importer.exists(name))
            assertFalse(File(homeDir, name).exists())
            assertFalse(importer.exists("bg-404.jpg"))
        }
}
