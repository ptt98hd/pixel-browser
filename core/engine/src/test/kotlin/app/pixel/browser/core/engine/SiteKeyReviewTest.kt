package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Review spec 015 – [siteKeyOf]: dấu chấm cuối, ký tự điều khiển, `%`. */
class SiteKeyReviewTest {
    @Test
    fun trailingDot_isTrimmed_beforeWwwStrip() {
        assertEquals("a.com", siteKeyOf("https://www.a.com./x"))
        assertEquals("a.com", siteKeyOf("https://a.com./"))
    }

    @Test
    fun onlyDots_returnNull_andBareWwwIsAHost() {
        assertNull(siteKeyOf("https://./"))
        assertEquals("www", siteKeyOf("https://www./"))
    }

    @Test
    fun percentOrControlCharsInHost_returnNull() {
        assertNull(siteKeyOf("https://a%2Eb.com/"))
        assertNull(siteKeyOf("https://a\u0001b.com/"))
        assertNull(siteKeyOf("https://a\tb.com/"))
    }
}
