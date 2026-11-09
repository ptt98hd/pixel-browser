package app.pixel.browser.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec 014 AC20 – mọi chuỗi có ở `values` (en) và `values-vi`, cùng tập khóa; có đủ chuỗi tối thiểu. (JVM.) */
class StringsParityTest {
    private fun keys(path: String): Set<String> {
        val file = File(path)
        assertTrue("missing $path", file.isFile)
        return Regex("<string\\s+name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun enAndVi_haveSameNonTrivialKeySet() {
        val en = keys("src/main/res/values/strings.xml")
        val vi = keys("src/main/res/values-vi/strings.xml")
        // 7 thông báo lỗi + 5 nhãn phần + "Sắp có" + tiêu đề/nhãn các trang: tối thiểu 25.
        assertTrue("expected at least 25 strings, got ${en.size}", en.size >= 25)
        assertEquals(en, vi)
    }

    @Test
    fun noKeyIsBlankInVi() {
        val text = File("src/main/res/values-vi/strings.xml").readText()
        assertTrue(Regex("<string[^>]*>\\s*</string>").findAll(text).none())
    }
}
