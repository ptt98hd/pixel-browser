package app.pixel.browser.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Spec 016 AC26 – mọi chuỗi của `:feature:browser` có ở `values` và `values-vi`, cùng tập khóa; chuỗi mới có mặt. */
class PrivateStringsParityTest {
    private fun keys(path: String): Set<String> {
        val file = File(path)
        assertTrue("missing $path", file.isFile)
        return Regex("<string\\s+name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun enAndVi_haveSameKeys() {
        assertEquals(keys("src/main/res/values/strings.xml"), keys("src/main/res/values-vi/strings.xml"))
    }

    @Test
    fun privateStrings_exist_inBothLocales() {
        val privateKeys = keys("src/main/res/values/strings.xml").filter { "private" in it }
        assertTrue("expected the new private-browsing strings, got $privateKeys", privateKeys.size >= 8)
        val vi = keys("src/main/res/values-vi/strings.xml")
        assertTrue(privateKeys.all { it in vi })
    }
}
