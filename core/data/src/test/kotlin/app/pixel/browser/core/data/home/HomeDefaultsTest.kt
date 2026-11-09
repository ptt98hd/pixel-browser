package app.pixel.browser.core.data.home

import app.pixel.browser.core.engine.isValidWebUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 010 – AC7: shortcut và màu mặc định. */
class HomeDefaultsTest {
    @Test
    fun ac7_defaultShortcuts_areExactlyFourInOrder() {
        val s = HomeDefaults.shortcuts
        assertEquals(listOf("Wikipedia", "YouTube", "GitHub", "OpenStreetMap"), s.map { it.title })
        assertEquals(
            listOf(
                "https://www.wikipedia.org/",
                "https://www.youtube.com/",
                "https://github.com/",
                "https://www.openstreetmap.org/",
            ),
            s.map { it.url },
        )
    }

    @Test
    fun ac7_defaultShortcuts_haveDistinctIds_andValidWebUrls() {
        val s = HomeDefaults.shortcuts
        assertEquals(s.size, s.map { it.id }.toSet().size)
        assertTrue(s.all { it.id.isNotBlank() })
        assertTrue(s.all { isValidWebUrl(it.url) })
    }

    @Test
    fun ac7_colors_areEightDistinct() {
        val c = HomeDefaults.colors
        assertEquals(8, c.size)
        assertEquals(8, c.toSet().size)
    }

    @Test
    fun ac7_maxShortcuts_isTwenty() {
        assertEquals(20, HomeDefaults.MAX_SHORTCUTS)
    }
}
