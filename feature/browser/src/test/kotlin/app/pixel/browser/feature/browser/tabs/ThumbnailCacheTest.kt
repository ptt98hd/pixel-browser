package app.pixel.browser.feature.browser.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 009 AC5 – `ThumbnailCache` LRU theo byte (JVM thuần). */
class ThumbnailCacheTest {
    private fun cache() = ThumbnailCache<String, String>(maxBytes = 100, sizeOf = { it.length })

    private fun bytes(n: Int) = "x".repeat(n)

    @Test
    fun ac5_put_accumulatesSizeBytes() {
        val c = cache()
        assertTrue(c.put("a", bytes(40)))
        assertTrue(c.put("b", bytes(40)))
        assertEquals(80L, c.sizeBytes)
        assertEquals(bytes(40), c["a"])
        assertEquals(bytes(40), c["b"])
    }

    @Test
    fun ac5_overflow_evictsLeastRecentlyAccessed() {
        val c = cache()
        c.put("a", bytes(40))
        c.put("b", bytes(40))
        c["a"] // a được dùng gần nhất → b là LRU
        c.put("c", bytes(40))
        assertNull(c["b"])
        assertEquals(bytes(40), c["a"])
        assertEquals(bytes(40), c["c"])
        assertEquals(80L, c.sizeBytes)
    }

    @Test
    fun ac5_overflow_withoutAnyGet_evictsOldestInserted() {
        val c = cache()
        c.put("a", bytes(40))
        c.put("b", bytes(40))
        c.put("c", bytes(40))
        assertNull(c["a"])
        assertEquals(bytes(40), c["b"])
        assertEquals(bytes(40), c["c"])
    }

    @Test
    fun ac5_valueLargerThanMax_isRejected_andEvictsNothing() {
        val c = cache()
        c.put("a", bytes(40))
        c.put("b", bytes(40))
        assertFalse(c.put("huge", bytes(101)))
        assertNull(c["huge"])
        assertEquals(bytes(40), c["a"])
        assertEquals(bytes(40), c["b"])
        assertEquals(80L, c.sizeBytes)
    }

    @Test
    fun ac5_putSameKey_replacesValue_andUpdatesSize() {
        val c = cache()
        c.put("a", bytes(40))
        assertTrue(c.put("a", bytes(10)))
        assertEquals(bytes(10), c["a"])
        assertEquals(10L, c.sizeBytes)
    }

    @Test
    fun ac5_remove_dropsEntry_andItsBytes() {
        val c = cache()
        c.put("a", bytes(40))
        c.put("b", bytes(30))
        c.remove("a")
        assertNull(c["a"])
        assertEquals(30L, c.sizeBytes)
        c.remove("missing")
        assertEquals(30L, c.sizeBytes)
    }

    @Test
    fun ac5_clear_emptiesCache() {
        val c = cache()
        c.put("a", bytes(40))
        c.put("b", bytes(30))
        c.clear()
        assertNull(c["a"])
        assertNull(c["b"])
        assertEquals(0L, c.sizeBytes)
    }

    @Test
    fun ac5_emptyCache_hasZeroSize() {
        assertEquals(0L, cache().sizeBytes)
        assertNull(cache()["a"])
    }
}
