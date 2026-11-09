package app.pixel.browser.feature.history

import app.pixel.browser.core.history.HistoryClearer
import app.pixel.browser.core.history.HistoryRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

/** Spec 012 AC14 – `HistoryViewModel` (JVM, `StandardTestDispatcher`). */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val utc = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L
    private val store = SpyHistoryStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newVm(): HistoryViewModel {
        val vm = HistoryViewModel(store, HistoryClearer(store, { now }), utc)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    private suspend fun seed(n: Int) = repeat(n) { store.insert("https://site$it.com/", "Site $it", now - it) }

    @Test
    fun beforeLoad_isNotEmpty_noDays_noLoadMore_thenDaysAreGrouped() =
        runTest(dispatcher) {
            seed(3)
            val vm = newVm()
            assertFalse(vm.state.value.isEmpty)
            assertTrue(
                vm.state.value.days
                    .isEmpty(),
            )
            assertFalse(vm.state.value.canLoadMore)
            advanceUntilIdle()
            val expected = daysOf(store.entries("", 100).first(), utc)
            assertEquals(expected, vm.state.value.days)
            assertFalse(vm.state.value.isEmpty)
        }

    @Test
    fun emptyStore_afterLoad_isEmpty_withBlankQuery() =
        runTest(dispatcher) {
            val vm = newVm()
            advanceUntilIdle()
            assertTrue(vm.state.value.isEmpty)
            assertEquals("", vm.state.value.query)
        }

    @Test
    fun query_filtersByStoreEntries_andTrimsWhitespace() =
        runTest(dispatcher) {
            store.insert("https://en.wikipedia.org/", "Wikipedia", now)
            store.insert("https://other.com/", "Other", now - 1)
            val vm = newVm()
            advanceUntilIdle()
            vm.onQueryChange("  wiki  ")
            advanceUntilIdle()
            assertTrue(store.entryQueries.any { it.first == "wiki" })
            assertFalse(store.entryQueries.any { it.first.startsWith(" ") || it.first.endsWith(" ") })
            assertEquals(
                listOf("https://en.wikipedia.org/"),
                vm.state.value.days
                    .flatMap { it.entries }
                    .map { it.url },
            )
        }

    @Test
    fun query_isDebounced300ms_onlyLastValueIsQueried() =
        runTest(dispatcher) {
            seed(1)
            val vm = newVm()
            advanceUntilIdle()
            store.entryQueries.clear()
            vm.onQueryChange("w")
            advanceTimeBy(100)
            vm.onQueryChange("wi")
            advanceTimeBy(100)
            vm.onQueryChange("wik")
            advanceUntilIdle()
            assertEquals(listOf("wik"), store.entryQueries.map { it.first })
        }

    @Test
    fun clearingQuery_returnsEverything() =
        runTest(dispatcher) {
            seed(3)
            val vm = newVm()
            advanceUntilIdle()
            vm.onQueryChange("site1")
            advanceUntilIdle()
            assertEquals(
                1,
                vm.state.value.days
                    .sumOf { it.entries.size },
            )
            vm.onQueryChange("")
            advanceUntilIdle()
            assertEquals(
                3,
                vm.state.value.days
                    .sumOf { it.entries.size },
            )
        }

    @Test
    fun firstPageIs100_canLoadMoreWhenFull_andLoadMoreAdds100() =
        runTest(dispatcher) {
            seed(150)
            val vm = newVm()
            advanceUntilIdle()
            assertEquals(100, store.entryQueries.last().second)
            assertEquals(
                100,
                vm.state.value.days
                    .sumOf { it.entries.size },
            )
            assertTrue(vm.state.value.canLoadMore)
            vm.onLoadMore()
            advanceUntilIdle()
            assertEquals(200, store.entryQueries.last().second)
            assertEquals(
                150,
                vm.state.value.days
                    .sumOf { it.entries.size },
            )
            assertFalse(vm.state.value.canLoadMore)
        }

    @Test
    fun fewerThanLimit_cannotLoadMore() =
        runTest(dispatcher) {
            seed(99)
            val vm = newVm()
            advanceUntilIdle()
            assertFalse(vm.state.value.canLoadMore)
        }

    @Test
    fun delete_callsStoreOnce_andListUpdates() =
        runTest(dispatcher) {
            seed(3)
            val vm = newVm()
            advanceUntilIdle()
            val victim =
                vm.state.value.days
                    .first()
                    .entries
                    .first()
            vm.onDelete(victim.id)
            advanceUntilIdle()
            assertEquals(listOf(victim.id), store.deleteCalls)
            assertEquals(
                2,
                vm.state.value.days
                    .flatMap { it.entries }
                    .size,
            )
            assertFalse(
                vm.state.value.days
                    .flatMap { it.entries }
                    .any { it.id == victim.id },
            )
        }

    @Test
    fun clear_runsClearerOnce_andRemovesEntriesInRange() =
        runTest(dispatcher) {
            store.insert("https://recent.com/", "", now - 1_000)
            store.insert("https://old.com/", "", now - 2 * 86_400_000L)
            val vm = newVm()
            advanceUntilIdle()
            vm.onClear(HistoryRange.LastDay)
            advanceUntilIdle()
            assertEquals(1, store.deleteBetweenCalls)
            assertEquals(
                listOf("https://old.com/"),
                vm.state.value.days
                    .flatMap { it.entries }
                    .map { it.url },
            )
        }

    @Test
    fun isEmpty_distinguishesNoHistoryFromNoMatch_byQuery() =
        runTest(dispatcher) {
            seed(2)
            val vm = newVm()
            advanceUntilIdle()
            vm.onQueryChange("zzz-no-match")
            advanceUntilIdle()
            assertTrue(vm.state.value.isEmpty)
            assertEquals("zzz-no-match", vm.state.value.query)
        }
}
