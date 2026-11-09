package app.pixel.browser.feature.bookmarks

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Spec 013 AC26 – `ContentResolverBookmarkFile`: giới hạn byte đếm khi đọc, lỗi không ném. */
@RunWith(RobolectricTestRunner::class)
class ContentResolverBookmarkFileTest {
    private val resolver = RuntimeEnvironment.getApplication().contentResolver
    private val gateway = ContentResolverBookmarkFile(resolver)
    private val uri = "content://test.docs/doc/1"

    private class EndlessStream : InputStream() {
        var served = 0L

        override fun read(): Int {
            served++
            return 'a'.code
        }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int {
            served += len
            b.fill('a'.code.toByte(), off, off + len)
            return len
        }
    }

    @Test
    fun read_withinLimit_returnsAllBytes() {
        val data = "<DL></DL>".toByteArray()
        shadowOf(resolver).registerInputStream(Uri.parse(uri), ByteArrayInputStream(data))
        val r = runBlocking { gateway.read(uri, 100) }
        assertTrue(r is FileRead.Bytes)
        assertArrayEquals(data, (r as FileRead.Bytes).bytes)
    }

    @Test
    fun read_exactlyMaxBytes_isBytes_oneMoreIsTooLarge() {
        shadowOf(resolver).registerInputStream(Uri.parse(uri), ByteArrayInputStream(ByteArray(100)))
        assertEquals(100, ((runBlocking { gateway.read(uri, 100) }) as FileRead.Bytes).bytes.size)
        shadowOf(resolver).registerInputStream(Uri.parse(uri), ByteArrayInputStream(ByteArray(101)))
        assertEquals(FileRead.TooLarge, runBlocking { gateway.read(uri, 100) })
    }

    @Test
    fun read_endlessStream_stopsAfterLimit_doesNotTrustSize() {
        val endless = EndlessStream()
        shadowOf(resolver).registerInputStream(Uri.parse(uri), endless)
        assertEquals(FileRead.TooLarge, runBlocking { gateway.read(uri, 1_000) })
        assertTrue("served ${endless.served}", endless.served < 1_000_000)
    }

    @Test
    fun read_unreadableOrMalformedUri_isUnreadable_notThrown() {
        assertEquals(FileRead.Unreadable, runBlocking { gateway.read("content://nobody.docs/missing", 100) })
        assertEquals(FileRead.Unreadable, runBlocking { gateway.read("not a uri at all", 100) })
        assertEquals(FileRead.Unreadable, runBlocking { gateway.read("", 100) })
    }

    @Test
    fun write_writesBytes_returnsTrue_andFalseWhenUnwritable() {
        val out = ByteArrayOutputStream()
        shadowOf(resolver).registerOutputStream(Uri.parse(uri), out)
        assertTrue(runBlocking { gateway.write(uri, "hello".toByteArray()) })
        assertArrayEquals("hello".toByteArray(), out.toByteArray())
        assertEquals(false, runBlocking { gateway.write("not a uri at all", "x".toByteArray()) })
    }
}
