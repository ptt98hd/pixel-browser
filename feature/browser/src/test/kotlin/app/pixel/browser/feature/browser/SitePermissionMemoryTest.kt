package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.SitePermission
import app.pixel.browser.feature.browser.web.SitePermissionMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 007 AC16 – bộ nhớ quyền theo origin, chỉ trong process. */
class SitePermissionMemoryTest {
    @Test
    fun ac16_unknown_isNull() {
        assertNull(SitePermissionMemory().get("https://a.com", SitePermission.Camera))
    }

    @Test
    fun ac16_remembersAllowAndBlock_perOriginAndPermission() {
        val m = SitePermissionMemory()
        m.set("https://a.com", SitePermission.Camera, true)
        m.set("https://a.com", SitePermission.Microphone, false)
        assertEquals(true, m.get("https://a.com", SitePermission.Camera))
        assertEquals(false, m.get("https://a.com", SitePermission.Microphone))
        assertNull(m.get("https://a.com", SitePermission.Location))
    }

    @Test
    fun ac16_otherOrigins_areIndependent() {
        val m = SitePermissionMemory()
        m.set("https://a.com", SitePermission.Camera, true)
        for (o in listOf("https://b.com", "http://a.com", "https://a.com:8443")) {
            assertNull(o, m.get(o, SitePermission.Camera))
        }
    }

    @Test
    fun ac16_laterChoice_overwrites() {
        val m = SitePermissionMemory()
        m.set("https://a.com", SitePermission.Camera, true)
        m.set("https://a.com", SitePermission.Camera, false)
        assertEquals(false, m.get("https://a.com", SitePermission.Camera))
    }

    @Test
    fun ac16_newInstance_isEmpty() {
        SitePermissionMemory().set("https://a.com", SitePermission.Camera, true)
        assertNull(SitePermissionMemory().get("https://a.com", SitePermission.Camera))
    }
}
