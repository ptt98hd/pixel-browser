package app.pixel.browser.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeConfigStore
import app.pixel.browser.core.data.home.Shortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Review spec 010: ghi đã xếp hàng không mất khi ViewModel bị hủy; một lần ghi lỗi không chặn các lần sau. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFlushTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val writerScope = TestScope(dispatcher)

    private val abc =
        HomeConfig(shortcuts = listOf("a", "b", "c").map { Shortcut(it, it.uppercase(), "https://$it.example/") })

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun create(
        store: HomeConfigStore,
        store0: ViewModelStore,
    ): HomeViewModel =
        ViewModelProvider(
            store0,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(store, FakeImageImporter(), dispatcher, writeScope = writerScope) as T
            },
        )[HomeViewModel::class.java]

    @Test
    fun queuedWrites_survive_onCleared() {
        val store = FakeHomeConfigStore(abc)
        val owner = ViewModelStore()
        val vm = create(store, owner)
        scope.advanceUntilIdle()
        vm.removeShortcut("a")
        vm.removeShortcut("b")
        owner.clear() // trước khi hàng đợi kịp chạy
        scope.advanceUntilIdle()
        assertEquals(2, store.writes.size)
        assertEquals(listOf("c"), store.stored!!.shortcuts.map { it.id })
    }

    @Test
    fun failingWrite_doesNotStopLaterWrites() {
        val inner = FakeHomeConfigStore(abc)
        var fail = true
        val store =
            object : HomeConfigStore by inner {
                override fun write(config: HomeConfig) {
                    if (fail) {
                        fail = false
                        error("disk full")
                    }
                    inner.write(config)
                }
            }
        val vm = create(store, ViewModelStore())
        scope.advanceUntilIdle()
        vm.removeShortcut("a")
        vm.removeShortcut("b")
        scope.advanceUntilIdle()
        assertEquals(listOf("c"), inner.stored!!.shortcuts.map { it.id })
    }
}
