package app.pixel.browser.core.data.search

import app.pixel.browser.core.omnibox.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 014 AC1 – kiểm tra engine tùy chỉnh (JVM thuần). */
class CustomEngineValidatorTest {
    private val good = "https://kagi.com/search?q=%s"

    private fun validate(
        name: String = "Kagi",
        template: String = good,
    ) = CustomEngineValidator.validate(name, template)

    private fun assertInvalidUrl(template: String) =
        assertEquals(template, CustomEngineResult.InvalidUrl, validate(template = template))

    @Test
    fun ac1_validInput_isOk() {
        assertEquals(CustomEngineResult.Ok(CustomSearchEngine("Kagi", good)), validate())
    }

    @Test
    fun ac1_nameAndTemplate_areTrimmed() {
        assertEquals(
            CustomEngineResult.Ok(CustomSearchEngine("Kagi", good)),
            validate(name = "  Kagi  ", template = "  $good\n"),
        )
    }

    @Test
    fun ac1_blankName_isNameEmpty() {
        assertEquals(CustomEngineResult.NameEmpty, validate(name = "  "))
        assertEquals(CustomEngineResult.NameEmpty, validate(name = ""))
    }

    @Test
    fun ac1_name40Chars_ok_41Chars_tooLong() {
        assertTrue(validate(name = "a".repeat(40)) is CustomEngineResult.Ok)
        assertEquals(CustomEngineResult.NameTooLong, validate(name = "a".repeat(41)))
    }

    @Test
    fun ac1_blankTemplate_isTemplateEmpty() {
        assertEquals(CustomEngineResult.TemplateEmpty, validate(template = ""))
        assertEquals(CustomEngineResult.TemplateEmpty, validate(template = "   "))
    }

    @Test
    fun ac1_noPlaceholder() {
        assertEquals(CustomEngineResult.NoPlaceholder, validate(template = "https://a.com/"))
    }

    @Test
    fun ac1_multiplePlaceholders() {
        assertEquals(CustomEngineResult.MultiplePlaceholders, validate(template = "https://a.com/?q=%s&r=%s"))
    }

    @Test
    fun ac1_placeholderInHost_isInvalidUrl() = assertInvalidUrl("https://%s.a.com/")

    @Test
    fun ac1_userinfo_isInvalidUrl() = assertInvalidUrl("https://u:p@a.com/?q=%s")

    @Test
    fun ac1_nonHttpScheme_isInvalidUrl() {
        assertInvalidUrl("ftp://a.com/?q=%s")
        assertInvalidUrl("javascript:%s")
    }

    @Test
    fun ac1_whitespaceInside_isInvalidUrl() = assertInvalidUrl("https://a.com/?q=%s x")

    @Test
    fun ac1_emptyHost_isInvalidUrl() = assertInvalidUrl("https:///?q=%s")

    @Test
    fun ac1_controlCharacters_areInvalidUrl() {
        assertInvalidUrl("https://a.com/?q=%s\u0000")
        assertInvalidUrl("https://a.com/\u0007?q=%s")
        assertInvalidUrl("https://a.com/?q=%s\u007F")
    }

    @Test
    fun ac1_templateOver2048_isTooLong() {
        val long = "https://a.com/?q=%s&x=" + "a".repeat(CustomEngineValidator.MAX_TEMPLATE)
        assertEquals(CustomEngineResult.TemplateTooLong, validate(template = long))
    }

    @Test
    fun ac1_templateExactly2048_isOk() {
        val prefix = "https://a.com/?q=%s&x="
        val t = prefix + "a".repeat(CustomEngineValidator.MAX_TEMPLATE - prefix.length)
        assertEquals(2048, t.length)
        assertTrue(validate(template = t) is CustomEngineResult.Ok)
    }

    @Test
    fun ac1_missingScheme_getsHttpsPrepended() {
        assertEquals(
            CustomEngineResult.Ok(CustomSearchEngine("Kagi", "https://a.com/search?q=%s")),
            validate(template = "a.com/search?q=%s"),
        )
    }

    @Test
    fun ac1_placeholderInPath_isValid() {
        assertTrue(validate(template = "https://a.com/s/%s/") is CustomEngineResult.Ok)
    }

    @Test
    fun ac1_otherPercentSequences_areStillValid() {
        assertTrue(validate(template = "https://a.com/?l=a%20b&q=%s") is CustomEngineResult.Ok)
        assertTrue(validate(template = "https://a.com/?p=100%&q=%s") is CustomEngineResult.Ok)
    }

    @Test
    fun ac1_isInsecure_trueOnlyForHttp() {
        assertTrue(CustomEngineValidator.isInsecure("http://a.com/?q=%s"))
        assertFalse(CustomEngineValidator.isInsecure("https://a.com/?q=%s"))
    }

    @Test
    fun ac1_httpTemplate_isAccepted() {
        assertEquals(
            CustomEngineResult.Ok(CustomSearchEngine("Local", "http://a.com/?q=%s")),
            validate(name = "Local", template = "http://a.com/?q=%s"),
        )
    }

    @Test
    fun ac1_everyOk_buildsSearchEngineWithoutThrowing() {
        val inputs =
            listOf(
                good,
                "a.com/search?q=%s",
                "https://a.com/s/%s/",
                "https://a.com/?l=a%20b&q=%s",
                "http://a.com:8080/?q=%s",
            )
        for (t in inputs) {
            val ok = validate(template = t) as CustomEngineResult.Ok
            SearchEngine("custom", ok.engine.name, ok.engine.template)
        }
    }

    @Test
    fun ac1_name_stripsControlAndBidiCharacters() {
        val dirty = "Ka\u202Egi\u0000\u2066\u2069\u200E\u200F\u202A\u202B\u202C\u202D\u007F\n"
        assertEquals(CustomEngineResult.Ok(CustomSearchEngine("Kagi", good)), validate(name = dirty))
    }

    @Test
    fun ac1_name_onlyInvisibleChars_isNameEmpty() {
        assertEquals(CustomEngineResult.NameEmpty, validate(name = "\u202E\u2066\u0000"))
        assertEquals(CustomEngineResult.NameEmpty, validate(name = "\u00A0\u2007"))
    }

    @Test
    fun ac1_name_lengthCountedAfterSanitizing() {
        assertTrue(validate(name = "a".repeat(40) + "\u202E\u200F") is CustomEngineResult.Ok)
    }

    @Test
    fun ac1_nbspAndFigureSpaceInTemplate_areInvalidUrl() {
        assertInvalidUrl("https://a.com/?q=%s\u00A0x")
        assertInvalidUrl("https://a.com/?q=%s\u2007x")
    }

    @Test
    fun ac1_surroundingNbsp_isTrimmed() {
        assertEquals(
            CustomEngineResult.Ok(CustomSearchEngine("Kagi", good)),
            validate(name = "\u00A0Kagi\u00A0", template = "\u00A0$good\u00A0"),
        )
    }
}
