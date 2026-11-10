package app.pixel.browser.core.data.site

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 015 AC14 – [DesktopSitePolicy] (JVM). */
class DesktopSitePolicyTest {
    private val persisted = mutableListOf<DesktopSiteData>()

    private fun policy(initial: DesktopSiteData = DesktopSiteData()) = DesktopSitePolicy(initial) { persisted += it }

    @Test
    fun ac14_defaults_areMobile_withNoOverrides() {
        val p = policy()
        assertFalse(p.resolve("a.com"))
        assertFalse(p.defaultDesktop.value)
        assertEquals(0, p.overrideCount.value)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun ac14_setDefaultTrue_appliesToEverySiteAndNull_andPersists() {
        val p = policy()
        p.setDefault(true)
        assertTrue(p.defaultDesktop.value)
        assertTrue(p.resolve("a.com"))
        assertTrue(p.resolve("other.org"))
        assertTrue(p.resolve(null))
        assertEquals(listOf(DesktopSiteData(defaultDesktop = true)), persisted)
    }

    @Test
    fun ac14_setSite_differentFromDefault_storesOverride_persistsOnce() {
        val p = policy()
        p.setSite("a.com", true)
        assertEquals(true, p.overrideFor("a.com"))
        assertEquals(1, p.overrideCount.value)
        assertTrue(p.resolve("a.com"))
        assertFalse(p.resolve("b.com"))
        assertEquals(listOf(DesktopSiteData(sites = mapOf("a.com" to true))), persisted)
    }

    @Test
    fun ac14_setSite_equalToDefault_removesEntry() {
        val p = policy()
        p.setSite("a.com", true)
        persisted.clear()
        p.setSite("a.com", false)
        assertNull(p.overrideFor("a.com"))
        assertEquals(0, p.overrideCount.value)
        assertFalse(p.resolve("a.com"))
        assertEquals(listOf(DesktopSiteData()), persisted)
    }

    @Test
    fun ac14_setSite_equalToDefault_whenNoEntry_storesNothing() {
        val p = policy()
        p.setSite("a.com", false)
        assertNull(p.overrideFor("a.com"))
        assertEquals(0, p.overrideCount.value)
    }

    @Test
    fun ac14_override_beatsDefault() {
        val p = policy()
        p.setDefault(true)
        p.setSite("b.com", false)
        assertFalse(p.resolve("b.com"))
        assertEquals(false, p.overrideFor("b.com"))
        assertTrue(p.resolve("c.com"))
        assertEquals(
            DesktopSiteData(defaultDesktop = true, sites = mapOf("b.com" to false)),
            persisted.last(),
        )
    }

    @Test
    fun ac14_setDefault_keepsOverrides() {
        val p = policy()
        p.setSite("a.com", true)
        p.setDefault(true)
        assertEquals(1, p.overrideCount.value)
        assertEquals(DesktopSiteData(defaultDesktop = true, sites = mapOf("a.com" to true)), persisted.last())
    }

    @Test
    fun ac14_clearOverrides_removesAll_keepsDefault_andPersists() {
        val p = policy(DesktopSiteData(defaultDesktop = true, sites = mapOf("a.com" to false, "b.com" to false)))
        p.clearOverrides()
        assertEquals(0, p.overrideCount.value)
        assertNull(p.overrideFor("a.com"))
        assertTrue(p.defaultDesktop.value)
        assertEquals(listOf(DesktopSiteData(defaultDesktop = true)), persisted)
    }

    @Test
    fun ac14_resolveNull_isTheDefault() {
        assertFalse(policy().resolve(null))
        assertTrue(policy(DesktopSiteData(defaultDesktop = true)).resolve(null))
    }

    @Test
    fun ac14_initialData_isHonoured() {
        val p = policy(DesktopSiteData(defaultDesktop = true, sites = mapOf("a.com" to false, "b.com" to true)))
        assertTrue(p.defaultDesktop.value)
        assertEquals(2, p.overrideCount.value)
        assertFalse(p.resolve("a.com"))
        assertTrue(p.resolve("b.com"))
        assertTrue(p.resolve("c.com"))
    }

    @Test
    fun ac14_site1001_evictsOldestOverride() {
        val p = policy()
        for (i in 1..DesktopSitePolicy.MAX_SITES) p.setSite("s$i.com", true)
        assertEquals(DesktopSitePolicy.MAX_SITES, p.overrideCount.value)
        p.setSite("s1001.com", true)
        assertEquals(DesktopSitePolicy.MAX_SITES, p.overrideCount.value)
        assertNull(p.overrideFor("s1.com"))
        assertEquals(true, p.overrideFor("s2.com"))
        assertEquals(true, p.overrideFor("s1001.com"))
        assertEquals(DesktopSitePolicy.MAX_SITES, persisted.last().sites.size)
    }

    @Test
    fun ac14_resettingAnOldSite_makesItNewest() {
        val p = policy()
        for (i in 1..DesktopSitePolicy.MAX_SITES) p.setSite("s$i.com", true)
        p.setSite("s1.com", true)
        p.setSite("s1001.com", true)
        assertEquals(true, p.overrideFor("s1.com"))
        assertNull(p.overrideFor("s2.com"))
        assertEquals(DesktopSitePolicy.MAX_SITES, p.overrideCount.value)
        assertEquals(
            "s1001.com",
            persisted
                .last()
                .sites.keys
                .last(),
        )
        assertEquals(
            "s1.com",
            persisted
                .last()
                .sites.keys
                .toList()[DesktopSitePolicy.MAX_SITES - 2],
        )
    }

    @Test
    fun ac14_blankSiteKey_isIgnored_andNotPersisted() {
        val p = policy()
        p.setSite("", true)
        p.setSite("   ", true)
        assertEquals(0, p.overrideCount.value)
        assertTrue(persisted.isEmpty())
    }

    @Test
    fun ac14_persistedSites_keepInsertionOrder() {
        val p = policy()
        p.setSite("c.com", true)
        p.setSite("a.com", true)
        p.setSite("b.com", true)
        assertEquals(
            listOf("c.com", "a.com", "b.com"),
            persisted
                .last()
                .sites.keys
                .toList(),
        )
    }

    @Test
    fun review_setDefault_unchanged_doesNotPersist() {
        val p = policy()
        p.setDefault(false)
        assertTrue(persisted.isEmpty())
        p.setDefault(true)
        p.setDefault(true)
        assertEquals(1, persisted.size)
    }
}
