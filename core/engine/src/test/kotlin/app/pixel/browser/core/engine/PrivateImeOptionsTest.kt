package app.pixel.browser.core.engine

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Spec 016 AC7 – hàm thuần `privateImeOptions` (JVM). */
class PrivateImeOptionsTest {
    private val noLearning = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

    @Test
    fun ac7_addsNoPersonalizedLearning_toZero() {
        assertNotEquals(0, privateImeOptions(0) and noLearning)
    }

    @Test
    fun ac7_keepsExistingBits() {
        val existing = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        val result = privateImeOptions(existing)
        assertEquals(existing, result and noLearning.inv())
        assertNotEquals(0, result and noLearning)
    }

    @Test
    fun ac7_isIdempotent() {
        assertEquals(privateImeOptions(0), privateImeOptions(privateImeOptions(0)))
    }
}

/** Review 016 – ô nhập của chính app ở tab ẩn danh: tắt gợi ý/tự sửa ở mức `inputType` (hàm thuần). */
class PrivateInputTypeTest {
    private val noSuggestions = android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    private val text = android.text.InputType.TYPE_CLASS_TEXT

    @Test
    fun textClass_getsNoSuggestionsFlag_keepingExistingBits() {
        val uri = text or android.text.InputType.TYPE_TEXT_VARIATION_URI
        val result = privateInputType(uri)
        assertEquals(uri, result and noSuggestions.inv())
        assertNotEquals(0, result and noSuggestions)
    }

    @Test
    fun nonTextClass_isUnchanged() {
        val number = android.text.InputType.TYPE_CLASS_NUMBER
        assertEquals(number, privateInputType(number))
    }

    @Test
    fun isIdempotent() {
        assertEquals(privateInputType(text), privateInputType(privateInputType(text)))
    }
}
