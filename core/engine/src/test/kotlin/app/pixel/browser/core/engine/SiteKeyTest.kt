package app.pixel.browser.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 015 AC12 – [siteKeyOf] (JVM). */
class SiteKeyTest {
    @Test
    fun ac12_lowercasesHost_stripsWww_dropsPathQueryFragment() {
        assertEquals("example.com", siteKeyOf("https://www.Example.com/a?b#c"))
    }

    @Test
    fun ac12_dropsPort_keepsSubdomain() {
        assertEquals("m.example.com", siteKeyOf("http://m.example.com:8080/"))
    }

    @Test
    fun ac12_stripsOnlyOneLeadingWww() {
        assertEquals("www.a.com", siteKeyOf("https://www.www.a.com"))
    }

    @Test
    fun ac12_wwwAndBareHost_shareKey_butSubdomainsDoNot() {
        assertEquals(siteKeyOf("https://a.com/"), siteKeyOf("https://www.a.com/"))
        assertEquals("m.a.com", siteKeyOf("https://m.a.com/"))
    }

    @Test
    fun ac12_ipv6Literal_keepsBrackets_dropsPort() {
        assertEquals("[::1]", siteKeyOf("https://[::1]:3000/"))
    }

    @Test
    fun ac12_idnHost_isPunycode() {
        assertEquals("xn--bcher-kva.de", siteKeyOf("https://bücher.de"))
    }

    @Test
    fun ac12_schemeIsCaseInsensitive() {
        assertEquals("a.com", siteKeyOf("HTTPS://A.com/"))
    }

    @Test
    fun ac12_nonWebSchemes_returnNull() {
        listOf("about:blank", "pixel://home", "file:///x", "javascript:1").forEach {
            assertNull(it, siteKeyOf(it))
        }
    }

    @Test
    fun ac12_missingHost_orEmpty_returnNull() {
        assertNull(siteKeyOf("https:///"))
        assertNull(siteKeyOf(""))
    }
}
