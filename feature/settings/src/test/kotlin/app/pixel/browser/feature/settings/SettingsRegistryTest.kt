package app.pixel.browser.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Spec 014 AC5–AC7 – registry dữ liệu (JVM thuần). */
class SettingsRegistryTest {
    private fun registry(
        sections: List<SettingsSection>,
        pages: List<SettingsPage>,
    ) = SettingsRegistry(sections, pages)

    private fun SettingsRegistry.layout() = groups.map { (s, ps) -> s.id to ps.map { it.id } }

    // --- AC5 ---

    @Test
    fun ac5_sectionsAreOrderedByOrderAscending() {
        val r =
            registry(
                listOf(section("b", 2), section("a", 1), section("c", 3)),
                listOf(page("p1", "c"), page("p2", "a"), page("p3", "b")),
            )
        assertEquals(listOf("a", "b", "c"), r.groups.map { it.first.id })
    }

    @Test
    fun ac5_pagesWithinSection_orderedByOrderThenId() {
        val r =
            registry(
                listOf(section("s", 0)),
                listOf(page("z", "s", 1), page("b", "s", 0), page("a", "s", 0), page("m", "s", 2)),
            )
        assertEquals(listOf("s" to listOf("a", "b", "z", "m")), r.layout())
    }

    @Test
    fun ac5_sectionWithoutPages_isHidden() {
        val r = registry(listOf(section("empty", 0), section("full", 1)), listOf(page("p", "full")))
        assertEquals(listOf("full"), r.groups.map { it.first.id })
    }

    @Test
    fun ac5_everyPageAppearsExactlyOnce() {
        val r =
            registry(
                listOf(section("a", 0), section("b", 1)),
                listOf(page("p1", "a"), page("p2", "b"), page("p3", "a", 5)),
            )
        val all = r.groups.flatMap { it.second }.map { it.id }
        assertEquals(listOf("p1", "p3", "p2"), all)
        assertEquals(all.distinct(), all)
    }

    // --- AC6 ---

    @Test(expected = IllegalArgumentException::class)
    fun ac6_duplicateRealPageIds_throw() {
        registry(listOf(section("s", 0)), listOf(page("x", "s"), page("x", "s", 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ac6_unknownSectionId_throws() {
        registry(listOf(section("s", 0)), listOf(page("x", "nope")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ac6_duplicateSectionIds_throw() {
        registry(listOf(section("s", 0), section("s", 1)), listOf(page("x", "s")))
    }

    @Test
    fun ac6_page_returnsPageOrNull() {
        val p = page("x", "s")
        val r = registry(listOf(section("s", 0)), listOf(p))
        assertSame(p, r.page("x"))
        assertNull(r.page("missing"))
    }

    // --- AC7 ---

    @Test
    fun ac7_realPageReplacesPlaceholderWithSameId() {
        val r =
            registry(
                listOf(section("privacy", 0)),
                listOf(page("clear_data", "privacy", placeholder = true), page("clear_data", "privacy")),
            )
        val pages = r.groups.single().second
        assertEquals(1, pages.size)
        assertEquals(false, pages.single().isPlaceholder)
        assertEquals(false, r.page("clear_data")!!.isPlaceholder)
    }

    @Test
    fun ac7_realPageInDifferentSection_takesRealPagePosition() {
        val r =
            registry(
                listOf(section("privacy", 0), section("site", 1)),
                listOf(
                    page("clear_data", "privacy", placeholder = true),
                    page("other", "privacy", 1),
                    page("clear_data", "site", 9),
                ),
            )
        assertEquals(listOf("privacy" to listOf("other"), "site" to listOf("clear_data")), r.layout())
    }

    @Test
    fun ac7_placeholderAloneStillShows() {
        val r = registry(listOf(section("s", 0)), listOf(page("clear_data", "s", placeholder = true)))
        val shown =
            r.groups
                .single()
                .second
                .single()
        assertEquals("clear_data", shown.id)
        assertEquals(true, shown.isPlaceholder)
        assertNotNull(r.page("clear_data"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ac7_twoPlaceholdersWithSameId_throw() {
        registry(
            listOf(section("s", 0)),
            listOf(page("clear_data", "s", placeholder = true), page("clear_data", "s", 1, placeholder = true)),
        )
    }

    @Test
    fun ac7_placeholderOrderAndRealReplacementDoNotDuplicateInGroups() {
        val r =
            registry(
                listOf(section("s", 0)),
                listOf(page("a", "s", 0, placeholder = true), page("b", "s", 1, placeholder = true), page("a", "s", 2)),
            )
        assertEquals(listOf("s" to listOf("b", "a")), r.layout())
    }
}
