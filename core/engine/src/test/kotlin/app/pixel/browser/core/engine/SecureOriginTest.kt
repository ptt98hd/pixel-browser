package app.pixel.browser.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 007 AC11 – [isSecureOrigin] (JVM). */
class SecureOriginTest {
    @Test
    fun ac11_secureOrigins() {
        for (o in listOf("https://a.com", "http://localhost:8080", "http://127.0.0.1", "http://[::1]")) {
            assertTrue(o, isSecureOrigin(o))
        }
    }

    @Test
    fun ac11_insecureOrigins() {
        for (o in listOf("http://a.com", "", "file://")) {
            assertFalse(o, isSecureOrigin(o))
        }
    }
}
