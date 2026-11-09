package app.pixel.browser.core.data.favicon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Spec 012 AC22 – `FileFaviconStore` (Robolectric, `@GraphicsMode(NATIVE)`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileFaviconStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(tmp.root, "favicons")
    }

    private fun png(size: Int = 16): ByteArray {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF336699.toInt())
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun files() = dir.listFiles().orEmpty().toList()

    @Test
    fun putThenGet_returnsDecodablePng() {
        val store = FileFaviconStore(dir)
        store.put("example.com", png())
        val bytes = store.get("example.com")
        assertNotNull(bytes)
        assertNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size))
    }

    @Test
    fun get_unknownHost_isNull() = assertNull(FileFaviconStore(dir).get("nope.com"))

    @Test
    fun file_isNamedBySha1OfHost() {
        FileFaviconStore(dir).put("example.com", png())
        assertEquals(listOf("${sha1("example.com")}.png"), files().map { it.name })
    }

    @Test
    fun largeImage_isDownscaledTo64Max() {
        val store = FileFaviconStore(dir)
        store.put("big.com", png(256))
        val bytes = store.get("big.com")!!
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("${bmp.width}x${bmp.height}", bmp.width <= 64 && bmp.height <= 64)
    }

    @Test
    fun undecodableBytes_areNotStored() {
        val store = FileFaviconStore(dir)
        store.put("bad.com", byteArrayOf(1, 2, 3, 4))
        assertNull(store.get("bad.com"))
        assertTrue(files().isEmpty())
    }

    @Test
    fun overMaxFiles_evictsLeastRecentlyUsed() {
        val store = FileFaviconStore(dir, maxFiles = 3)
        listOf("a.com", "b.com", "c.com").forEachIndexed { i, h ->
            store.put(h, png())
            File(dir, "${sha1(h)}.png").setLastModified(1_000L * (i + 1))
        }
        store.get("a.com") // chạm a.com: b.com trở thành cũ nhất
        store.put("d.com", png())
        assertNull(store.get("b.com"))
        assertNotNull(store.get("a.com"))
        assertNotNull(store.get("c.com"))
        assertNotNull(store.get("d.com"))
        assertEquals(3, files().size)
    }

    @Test
    fun overMaxBytes_evictsUntilWithinBudget() {
        val one = png(16).size.toLong()
        val store = FileFaviconStore(dir, maxFiles = 500, maxBytes = one * 2)
        listOf("a.com", "b.com", "c.com").forEachIndexed { i, h ->
            store.put(h, png(16))
            File(dir, "${sha1(h)}.png").setLastModified(1_000L * (i + 1))
        }
        assertTrue(files().sumOf { it.length() } <= one * 2)
        assertNotNull(store.get("c.com"))
    }

    @Test
    fun retainOnly_removesOtherHosts() {
        val store = FileFaviconStore(dir)
        store.put("keep.com", png())
        store.put("drop.com", png())
        store.retainOnly(setOf("keep.com"))
        assertNotNull(store.get("keep.com"))
        assertNull(store.get("drop.com"))
    }

    @Test
    fun put_deletesOrphanTmpFiles() {
        dir.mkdirs()
        File(dir, "stale.png.tmp").writeBytes(byteArrayOf(1))
        FileFaviconStore(dir).put("a.com", png())
        assertTrue(files().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun clear_removesEverything() {
        val store = FileFaviconStore(dir)
        store.put("a.com", png())
        store.clear()
        assertNull(store.get("a.com"))
        assertTrue(files().isEmpty())
    }
}
