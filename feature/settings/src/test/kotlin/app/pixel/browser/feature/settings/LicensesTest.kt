package app.pixel.browser.feature.settings

import app.pixel.browser.core.engine.isValidWebUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 014 AC16 (phần dữ liệu) – danh sách giấy phép bên thứ ba (JVM thuần). */
class LicensesTest {
    private val allowedSpdx =
        setOf("Apache-2.0", "GPL-3.0", "MIT", "BSD-3-Clause", "BSD-2-Clause", "ISC", "MPL-2.0", "EPL-2.0")

    private val all get() = Licenses.all

    @Test
    fun ac16_licenses_notEmpty() {
        assertTrue(all.isNotEmpty())
    }

    @Test
    fun ac16_names_areUnique_andNotBlank() {
        assertTrue(all.all { it.name.isNotBlank() })
        assertEquals(all.map { it.name }.distinct(), all.map { it.name })
    }

    @Test
    fun ac16_everyUrl_isValidWebUrl() {
        val bad = all.filterNot { isValidWebUrl(it.url) }
        assertTrue("invalid urls: $bad", bad.isEmpty())
    }

    @Test
    fun ac16_everyLicense_isInAllowedSpdxList() {
        val bad = all.filter { it.license !in allowedSpdx }
        assertTrue("unexpected licenses: $bad", bad.isEmpty())
    }

    @Test
    fun ac16_containsRequiredLibraries() {
        val names = all.map { it.name.lowercase() }
        listOf("compose", "kotlin", "datastore", "graphics shapes", "room").forEach { needle ->
            assertTrue("missing $needle in $names", names.any { needle in it })
        }
    }
}
