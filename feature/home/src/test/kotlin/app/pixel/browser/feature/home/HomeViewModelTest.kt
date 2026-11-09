package app.pixel.browser.feature.home

import app.pixel.browser.core.data.home.HomeBackground
import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeDefaults
import app.pixel.browser.core.data.home.Shortcut
import app.pixel.browser.core.data.home.ShortcutEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Spec 010 – AC11–AC13: [HomeViewModel] trên JVM với store/importer giả. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val log = EventLog()

    private val sa = Shortcut("a", "A", "https://a.example/x")
    private val sb = Shortcut("b", "B", "https://b.example/x")
    private val sc = Shortcut("c", "C", "https://c.example/x")
    private val sd = Shortcut("d", "D", "https://d.example/x")
    private val abcd = HomeConfig(shortcuts = listOf(sa, sb, sc, sd))

    private lateinit var store: FakeHomeConfigStore
    private lateinit var importer: FakeImageImporter

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private var idCounter = 0

    private fun vm(
        stored: HomeConfig? = abcd,
        existing: Set<String> = emptySet(),
    ): HomeViewModel {
        store = FakeHomeConfigStore(stored, log)
        importer = FakeImageImporter(existing = existing.toMutableSet(), log = log)
        return HomeViewModel(store, importer, dispatcher, newId = { "id-${++idCounter}" })
    }

    private fun loaded(
        stored: HomeConfig? = abcd,
        existing: Set<String> = emptySet(),
    ): HomeViewModel =
        vm(stored, existing).also {
            scope.advanceUntilIdle()
            log.events.clear()
        }

    private fun idle() = scope.advanceUntilIdle()

    private fun HomeViewModel.ids() = config.value!!.shortcuts.map { it.id }

    // --- AC11 ---

    @Test
    fun ac11_beforeIoRuns_configIsNull() {
        val vm = vm()
        assertNull(vm.config.value)
    }

    @Test
    fun ac11_emptyStore_loadsDefaults_withoutWriting() {
        val vm = vm(stored = null)
        idle()
        assertEquals(HomeConfig(shortcuts = HomeDefaults.shortcuts), vm.config.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun ac11_storedConfig_isLoadedAsIs() {
        val stored = abcd.copy(background = HomeBackground.Solid(0xFF112233.toInt()))
        val vm = vm(stored = stored)
        idle()
        assertEquals(stored, vm.config.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun ac11_imageBackgroundWithMissingFile_fallsBackToDynamic() {
        val vm = vm(stored = abcd.copy(background = HomeBackground.Image("bg-9.jpg", 0.5f)), existing = emptySet())
        idle()
        assertEquals(HomeBackground.Dynamic, vm.config.value!!.background)
        assertEquals(abcd.shortcuts, vm.config.value!!.shortcuts)
    }

    @Test
    fun ac11_imageBackgroundWithExistingFile_isKept() {
        val bg = HomeBackground.Image("bg-9.jpg", 0.5f)
        val vm = vm(stored = abcd.copy(background = bg), existing = setOf("bg-9.jpg"))
        idle()
        assertEquals(bg, vm.config.value!!.background)
    }

    // --- AC12 ---

    @Test
    fun ac12_addShortcut_updatesSynchronously_andWritesOnceOnIo() {
        val vm = loaded()
        val result = vm.addShortcut("", "example.com")
        assertTrue(result is ShortcutEdit.Ok)
        assertEquals(listOf("a", "b", "c", "d", "id-1"), vm.ids())
        assertEquals(
            "https://example.com",
            vm.config.value!!
                .shortcuts
                .last()
                .url,
        )
        assertTrue("write must happen on io, not inline", store.writes.isEmpty())
        idle()
        assertEquals(listOf(vm.config.value), store.writes)
    }

    @Test
    fun ac12_addShortcut_rejected_doesNotWrite() {
        val vm = loaded()
        assertSame(ShortcutEdit.Invalid, vm.addShortcut("", "hello world"))
        assertSame(ShortcutEdit.Duplicate, vm.addShortcut("", sa.url))
        idle()
        assertEquals(abcd, vm.config.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun ac12_updateShortcut_updatesAndWritesOnce() {
        val vm = loaded()
        assertTrue(vm.updateShortcut("b", "New", "example.com") is ShortcutEdit.Ok)
        assertEquals(Shortcut("b", "New", "https://example.com"), vm.config.value!!.shortcuts[1])
        idle()
        assertEquals(1, store.writes.size)
        assertEquals(vm.config.value, store.writes.single())
    }

    @Test
    fun ac12_removeShortcut_updatesAndWritesOnce() {
        val vm = loaded()
        vm.removeShortcut("b")
        assertEquals(listOf("a", "c", "d"), vm.ids())
        idle()
        assertEquals(1, store.writes.size)
    }

    @Test
    fun ac12_moveShortcut_updatesAndWritesOnce() {
        val vm = loaded()
        vm.moveShortcut(0, 2)
        assertEquals(listOf("b", "c", "a", "d"), vm.ids())
        idle()
        assertEquals(1, store.writes.size)
        assertEquals(
            listOf("b", "c", "a", "d"),
            store.writes
                .single()
                .shortcuts
                .map { it.id },
        )
    }

    @Test
    fun ac12_setBackground_updatesAndWritesOnce() {
        val vm = loaded()
        val solid = HomeBackground.Solid(0xFF112233.toInt())
        vm.setBackground(solid)
        assertEquals(solid, vm.config.value!!.background)
        idle()
        assertEquals(listOf(solid), store.writes.map { it.background })
    }

    @Test
    fun ac12_setDim_onImage_updatesClampedAndWritesOnce() {
        val vm = loaded(abcd.copy(background = HomeBackground.Image("bg-1.jpg", 0.3f)), existing = setOf("bg-1.jpg"))
        vm.setDim(0.6f)
        assertEquals(HomeBackground.Image("bg-1.jpg", 0.6f), vm.config.value!!.background)
        vm.setDim(5f)
        assertEquals(HomeBackground.Image("bg-1.jpg", 0.8f), vm.config.value!!.background)
        idle()
        assertEquals(2, store.writes.size)
    }

    @Test
    fun ac12_multipleEdits_writtenSequentially_lastWins() {
        val vm = loaded()
        vm.removeShortcut("a")
        vm.moveShortcut(0, 2)
        vm.addShortcut("X", "x.example.com")
        idle()
        assertEquals(3, store.writes.size)
        assertEquals(vm.config.value, store.writes.last())
        assertEquals(vm.config.value, store.stored)
    }

    @Test
    fun ac12_editsBeforeLoad_areIgnored() {
        val vm = vm()
        vm.addShortcut("", "example.com")
        vm.removeShortcut("a")
        vm.moveShortcut(0, 3)
        vm.setBackground(HomeBackground.Solid(0xFF112233.toInt()))
        vm.setDim(0.5f)
        assertNull(vm.config.value)
        idle()
        assertEquals(abcd, vm.config.value)
        assertTrue(store.writes.isEmpty())
    }

    // --- AC13 ---

    @Test
    fun ac13_imagePicked_importsOnce_andSetsImageBackgroundWithDefaultDim() {
        val vm = loaded()
        vm.onImagePicked("content://x/1")
        idle()
        assertEquals(listOf("content://x/1"), importer.imported)
        assertEquals(HomeBackground.Image("bg-1.jpg", 0.3f), vm.config.value!!.background)
        assertEquals(HomeBackground.Image("bg-1.jpg", 0.3f), store.writes.last().background)
        assertNull(vm.message.value)
    }

    @Test
    fun ac13_imagePicked_whenAlreadyImage_keepsDim_andDeletesOldFileAfterWrite() {
        val vm = loaded(abcd.copy(background = HomeBackground.Image("bg-0.jpg", 0.5f)), existing = setOf("bg-0.jpg"))
        importer.result = "bg-1.jpg"
        vm.onImagePicked("content://x/1")
        idle()
        assertEquals(HomeBackground.Image("bg-1.jpg", 0.5f), vm.config.value!!.background)
        assertEquals(listOf("bg-0.jpg"), importer.deleted)
        val write = log.events.lastIndexOf("write")
        val delete = log.events.indexOf("delete:bg-0.jpg")
        assertTrue("old image must be deleted after new config is written: ${log.events}", write in 0 until delete)
    }

    @Test
    fun ac13_importFails_keepsBackground_andShowsImageFailed() {
        val vm = loaded()
        importer.result = null
        vm.onImagePicked("content://x/1")
        idle()
        assertEquals(HomeBackground.Dynamic, vm.config.value!!.background)
        assertEquals(HomeMessage.ImageFailed, vm.message.value)
        assertTrue(store.writes.isEmpty())
        vm.onMessageShown()
        assertNull(vm.message.value)
    }

    @Test
    fun ac13_pickerCancelled_doesNothing() {
        val vm = loaded()
        vm.onImagePicked(null)
        idle()
        assertTrue(importer.imported.isEmpty())
        assertNull(vm.message.value)
        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun ac13_switchingFromImageToDynamic_deletesImageFile() {
        val vm = loaded(abcd.copy(background = HomeBackground.Image("bg-0.jpg", 0.5f)), existing = setOf("bg-0.jpg"))
        vm.setBackground(HomeBackground.Dynamic)
        idle()
        assertEquals(listOf("bg-0.jpg"), importer.deleted)
    }

    @Test
    fun ac13_switchingFromImageToSolid_deletesImageFile() {
        val vm = loaded(abcd.copy(background = HomeBackground.Image("bg-0.jpg", 0.5f)), existing = setOf("bg-0.jpg"))
        vm.setBackground(HomeBackground.Solid(0xFF112233.toInt()))
        idle()
        assertEquals(listOf("bg-0.jpg"), importer.deleted)
    }

    @Test
    fun ac13_switchingBetweenNonImageBackgrounds_deletesNothing() {
        val vm = loaded()
        vm.setBackground(HomeBackground.Solid(0xFF112233.toInt()))
        vm.setBackground(HomeBackground.Dynamic)
        idle()
        assertTrue(importer.deleted.isEmpty())
    }
}
