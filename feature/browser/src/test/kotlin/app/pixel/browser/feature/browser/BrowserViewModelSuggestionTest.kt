package app.pixel.browser.feature.browser

import app.pixel.browser.core.history.HistorySuggestionProvider
import app.pixel.browser.core.history.InMemoryHistoryStore
import app.pixel.browser.core.omnibox.DefaultSuggestionProvider
import app.pixel.browser.core.omnibox.SearchEngines
import app.pixel.browser.core.omnibox.SuggestionAggregator
import app.pixel.browser.core.omnibox.SuggestionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 012 AC13 – omnibox dùng aggregator [Default, History] (JVM). */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelSuggestionTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val store = InMemoryHistoryStore()
    private val now = 1_000_000_000L

    private fun seed() =
        runBlocking {
            store.insert("https://en.wikipedia.org/wiki/Kotlin", "Kotlin - Wikipedia", now - 1)
            store.insert("https://en.wikipedia.org/wiki/Java", "Java - Wikipedia", now - 2)
            store.insert("https://wiki.org", "Wiki home", now - 3)
            store.insert("https://wiki.org/page", "Wiki page", now - 4)
            store.insert("https://wikia.com/", "", now - 5)
            store.insert("https://wikivoyage.org/", "Voyage", now - 6)
        }

    private val aggregator by lazy {
        SuggestionAggregator(
            listOf(DefaultSuggestionProvider(SearchEngines.Default), HistorySuggestionProvider(store, { now })),
        )
    }

    private fun fixtureWithSuggestions(): Pair<BrowserFixture, BrowserViewModel> {
        val f = BrowserFixture()
        val vm = BrowserViewModel(f.tabManager, suggestionProvider = aggregator)
        return f to vm
    }

    @Test
    fun ac13_typingWiki_firstRowIsDefault_restAreHistory_atMost8() {
        seed()
        val (_, vm) = fixtureWithSuggestions()
        vm.onStartEditing()
        vm.onQueryChange("wiki")
        val s = vm.uiState.value.suggestions
        assertTrue(s.first().kind == SuggestionKind.Search || s.first().kind == SuggestionKind.GoTo)
        assertTrue("history rows expected: $s", s.size > 1)
        assertTrue(s.drop(1).all { it.kind == SuggestionKind.History })
        assertTrue(s.size <= 8)
    }

    @Test
    fun ac13_historyUrlEqualToGoToUrl_isDeduped() {
        seed()
        val (_, vm) = fixtureWithSuggestions()
        vm.onStartEditing()
        vm.onQueryChange("wiki.org")
        val s = vm.uiState.value.suggestions
        assertEquals(SuggestionKind.GoTo, s.first().kind)
        assertEquals(s.map { it.url }.distinct(), s.map { it.url })
        assertTrue("https://wiki.org/page" in s.map { it.url })
        assertEquals(1, s.count { it.url == "https://wiki.org" })
    }

    @Test
    fun ac13_tapHistorySuggestion_loadsUrl_andLeavesHome() {
        seed()
        val (f, vm) = fixtureWithSuggestions()
        assertTrue(vm.uiState.value.isHome)
        vm.onStartEditing()
        vm.onQueryChange("wiki")
        val history =
            vm.uiState.value.suggestions
                .first { it.kind == SuggestionKind.History }
        vm.onSuggestionClick(history)
        assertFalse(vm.uiState.value.isHome)
        assertEquals(history.url, f.selected().loadUrlCalls.last())
    }

    @Test
    fun ac13_privateTab_stillSeesHistorySuggestions() {
        seed()
        val f = BrowserFixture()
        f.tabManager.openTab("https://p.com/", select = true, isPrivate = true)
        val vm = BrowserViewModel(f.tabManager, suggestionProvider = aggregator)
        vm.onStartEditing()
        vm.onQueryChange("wiki")
        assertTrue(
            vm.uiState.value.suggestions
                .any { it.kind == SuggestionKind.History },
        )
    }
}
