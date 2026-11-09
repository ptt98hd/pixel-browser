package app.pixel.browser.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec 012 AC21 – mọi chuỗi có ở `values` (en) và `values-vi`, cùng tập khóa. (JVM, đọc XML nguồn.) */
class StringsParityTest {
    private fun keys(path: String): Set<String> {
        val file = File(path)
        assertTrue("missing $path", file.isFile)
        return Regex("<string\\s+name=\"([^\"]+)\"")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun enAndVi_haveSameNonTrivialKeySet() {
        val en = keys("src/main/res/values/strings.xml")
        val vi = keys("src/main/res/values-vi/strings.xml")
        assertTrue("expected at least 10 strings, got ${en.size}", en.size >= 10)
        assertEquals(en, vi)
    }
}
