package app.pixel.browser.feature.browser.tabs

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 009 AC7 – `tabGridColumns` (JVM thuần). */
class TabGridColumnsTest {
    @Test
    fun ac7_compactWidths_haveTwoColumns() {
        assertEquals(2, tabGridColumns(320))
        assertEquals(2, tabGridColumns(411))
        assertEquals(2, tabGridColumns(599))
    }

    @Test
    fun ac7_mediumWidths_haveThreeColumns() {
        assertEquals(3, tabGridColumns(600))
        assertEquals(3, tabGridColumns(839))
    }

    @Test
    fun ac7_expandedWidths_haveFourColumns() {
        assertEquals(4, tabGridColumns(840))
        assertEquals(4, tabGridColumns(1280))
    }
}
