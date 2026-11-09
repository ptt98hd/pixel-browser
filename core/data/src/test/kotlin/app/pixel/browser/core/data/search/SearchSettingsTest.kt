package app.pixel.browser.core.data.search

import app.pixel.browser.core.omnibox.SearchEngine
import app.pixel.browser.core.omnibox.SearchEngines
import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 014 AC2 – engine hiệu lực (JVM thuần). */
class SearchSettingsTest {
    private val kagi = CustomSearchEngine("Kagi", "https://kagi.com/search?q=%s")

    @Test
    fun ac2_default_isGoogle() {
        assertEquals(SearchEngines.Google, SearchSettings().engine)
    }

    @Test
    fun ac2_eachBuiltInId_mapsToThatEngine() {
        for (engine in SearchEngines.all) {
            assertEquals(engine, SearchSettings(selectedId = engine.id).engine)
        }
    }

    @Test
    fun ac2_customSelectedAndValid_buildsCustomEngine() {
        assertEquals(
            SearchEngine("custom", "Kagi", "https://kagi.com/search?q=%s"),
            SearchSettings(SearchEngineIds.CUSTOM, kagi).engine,
        )
    }

    @Test
    fun ac2_customSelectedButNull_fallsBackToGoogle() {
        assertEquals(SearchEngines.Google, SearchSettings(SearchEngineIds.CUSTOM, null).engine)
    }

    @Test
    fun ac2_customSelectedButInvalid_fallsBackToGoogle() {
        val broken = CustomSearchEngine("Broken", "https://a.com/")
        assertEquals(SearchEngines.Google, SearchSettings(SearchEngineIds.CUSTOM, broken).engine)
    }

    @Test
    fun ac2_customSelectedWithUntrustedTemplate_neverThrows() {
        val evil = CustomSearchEngine("Evil", "https://%s.evil.com/")
        assertEquals(SearchEngines.Google, SearchSettings(SearchEngineIds.CUSTOM, evil).engine)
        val blankName = CustomSearchEngine("  ", "https://a.com/?q=%s")
        assertEquals(SearchEngines.Google, SearchSettings(SearchEngineIds.CUSTOM, blankName).engine)
    }

    @Test
    fun ac2_unknownId_fallsBackToGoogle() {
        assertEquals(SearchEngines.Google, SearchSettings(selectedId = "yahoo").engine)
    }

    @Test
    fun ac2_customIsKept_whenBuiltInSelected() {
        val settings = SearchSettings(selectedId = "bing", custom = kagi)
        assertEquals(SearchEngines.Bing, settings.engine)
        assertEquals(kagi, settings.custom)
    }
}
