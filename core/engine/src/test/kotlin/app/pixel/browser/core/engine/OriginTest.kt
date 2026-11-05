package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 006 – AC3: [originOf] (JVM). */
class OriginTest {
    @Test
    fun ac3_lowercasesHost_andDropsPathAndQuery() {
        assertEquals("https://example.com", originOf("https://Example.com/a?b"))
    }

    @Test
    fun ac3_dropsDefaultHttpsPort() {
        assertEquals("https://example.com", originOf("https://example.com:443/"))
    }

    @Test
    fun ac3_keepsNonDefaultPort() {
        assertEquals("http://example.com:8080", originOf("http://example.com:8080/x"))
    }

    @Test
    fun ac3_ipv6_dropsDefaultHttpPort() {
        assertEquals("http://[::1]", originOf("http://[::1]:80/"))
    }

    @Test
    fun ac3_nonWebOrInvalidUrls_haveEmptyOrigin() {
        for (url in listOf("about:blank", "file:///a", "", "%%")) {
            assertEquals("originOf(\"$url\")", "", originOf(url))
        }
    }
}
