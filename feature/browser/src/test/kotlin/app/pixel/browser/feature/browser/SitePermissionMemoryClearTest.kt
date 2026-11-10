package app.pixel.browser.feature.browser

import app.pixel.browser.core.engine.SitePermission
import app.pixel.browser.feature.browser.web.SitePermissionMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec 016 – `SitePermissionMemory.clear()` xóa mọi lựa chọn (bộ nhớ quyền của tab ẩn danh). */
class SitePermissionMemoryClearTest {
    @Test
    fun clear_removesEveryChoice() {
        val memory = SitePermissionMemory()
        memory.set("https://a.com", SitePermission.Camera, true)
        memory.set("https://b.com", SitePermission.Location, false)
        memory.clear()
        assertNull(memory.get("https://a.com", SitePermission.Camera))
        assertNull(memory.get("https://b.com", SitePermission.Location))
    }

    @Test
    fun clear_onEmptyMemory_isHarmless_andMemoryIsReusable() {
        val memory = SitePermissionMemory()
        memory.clear()
        memory.set("https://a.com", SitePermission.Microphone, true)
        assertEquals(true, memory.get("https://a.com", SitePermission.Microphone))
    }
}
