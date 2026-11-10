package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Spec 015 AC13 – [DesktopUserAgent.derive] (JVM). */
class DesktopUserAgentTest {
    private val webViewUa =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A.240305.019.A1; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/126.0.6478.122 Mobile Safari/537.36"
    private val chromeAndroidUa =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    @Test
    fun ac13_webViewUa_becomesChromeDesktop() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.6478.122 Safari/537.36",
            DesktopUserAgent.derive(webViewUa),
        )
    }

    @Test
    fun ac13_chromeAndroidUa_withoutWvOrVersion_alsoConverts() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
            DesktopUserAgent.derive(chromeAndroidUa),
        )
    }

    @Test
    fun ac13_uaWithoutLinuxAndroidBlock_onlyDropsMobileToken() {
        val ua = "Mozilla/5.0 (Foo) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
        assertEquals("Mozilla/5.0 (Foo) AppleWebKit/537.36 Chrome/126.0.0.0 Safari/537.36", DesktopUserAgent.derive(ua))
    }

    @Test
    fun ac13_emptyString_staysEmpty() {
        assertEquals("", DesktopUserAgent.derive(""))
    }

    @Test
    fun ac13_result_hasNoAndroidMobileWvOrVersionTokens() {
        listOf(webViewUa, chromeAndroidUa).forEach { ua ->
            val out = DesktopUserAgent.derive(ua)
            listOf("Android", "Mobile", "wv", "Version/").forEach { token ->
                assertFalse("$token in $out", out.contains(token))
            }
        }
    }

    @Test
    fun ac13_isIdempotent() {
        listOf(webViewUa, chromeAndroidUa, "", "Mozilla/5.0 (Foo) Mobile Safari").forEach {
            val once = DesktopUserAgent.derive(it)
            assertEquals(once, DesktopUserAgent.derive(once))
        }
    }
}
