package app.pixel.browser.core.ui

import androidx.compose.material3.ColorScheme
import org.junit.Assert.assertEquals

/**
 * Mọi màu của [ColorScheme] theo tên getter. `ColorScheme` không override `equals` (material3 1.4.0) nên so sánh
 * từng màu thay vì `==`.
 */
fun ColorScheme.colorValues(): Map<String, Long> =
    ColorScheme::class.java.methods
        .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
        .associate { it.name.substringBefore('-') to it.invoke(this) as Long }

fun assertSameColors(
    message: String,
    expected: ColorScheme,
    actual: ColorScheme,
) {
    val values = expected.colorValues()
    check(values.size > 30) { "Không đọc được màu của ColorScheme (${values.size})" }
    assertEquals(message, values, actual.colorValues())
}
