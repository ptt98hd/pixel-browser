package app.pixel.browser.core.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 010 – AC8, AC9: [HomeConfigEditor] thuần JVM. */
class HomeConfigEditorTest {
    private val sa = Shortcut("a", "A", "https://a.example/x")
    private val sb = Shortcut("b", "B", "https://b.example/x")
    private val sc = Shortcut("c", "C", "https://c.example/x")
    private val sd = Shortcut("d", "D", "https://d.example/x")
    private val base = HomeConfig(shortcuts = listOf(sa, sb, sc, sd))

    private var counter = 0

    private fun nextId(): String = "new-${++counter}"

    private fun ShortcutEdit.ok(): HomeConfig {
        assertTrue("expected Ok but was $this", this is ShortcutEdit.Ok)
        return (this as ShortcutEdit.Ok).config
    }

    private fun HomeConfig.ids() = shortcuts.map { it.id }

    // --- AC8: add ---

    @Test
    fun ac8_add_bareDomain_normalizesUrl_andUsesHostAsTitle() {
        val c = HomeConfigEditor.add(base, "", "example.com", ::nextId).ok()
        val added = c.shortcuts.last()
        assertEquals(5, c.shortcuts.size)
        assertEquals("https://example.com", added.url)
        assertEquals("example.com", added.title)
        assertEquals("new-1", added.id)
        assertEquals(base.shortcuts, c.shortcuts.dropLast(1))
    }

    @Test
    fun ac8_add_trimsTitle() {
        val c = HomeConfigEditor.add(base, "  Ví dụ  ", "https://example.com/a", ::nextId).ok()
        assertEquals("Ví dụ", c.shortcuts.last().title)
        assertEquals("https://example.com/a", c.shortcuts.last().url)
    }

    @Test
    fun ac8_add_blankTitle_usesHostWithoutWww() {
        val c = HomeConfigEditor.add(base, "   ", "https://www.example.org/p", ::nextId).ok()
        assertEquals("example.org", c.shortcuts.last().title)
    }

    @Test
    fun ac8_add_invalidInputs_areRejected() {
        for (input in listOf("hello world", "javascript:alert(1)", "file:///a", "", "   ", "about:blank")) {
            assertSame("input '$input'", ShortcutEdit.Invalid, HomeConfigEditor.add(base, "T", input, ::nextId))
        }
    }

    @Test
    fun ac8_add_existingNormalizedUrl_isDuplicate() {
        val c = HomeConfigEditor.add(base, "", "example.com", ::nextId).ok()
        assertSame(ShortcutEdit.Duplicate, HomeConfigEditor.add(c, "Other", "example.com", ::nextId))
        assertSame(ShortcutEdit.Duplicate, HomeConfigEditor.add(base, "", sa.url, ::nextId))
    }

    @Test
    fun ac8_add_whenTwentyShortcuts_isFull() {
        val full =
            HomeConfig(
                shortcuts = List(HomeDefaults.MAX_SHORTCUTS) { Shortcut("s$it", "S$it", "https://s$it.example/") },
            )
        assertSame(ShortcutEdit.Full, HomeConfigEditor.add(full, "", "example.com", ::nextId))
    }

    @Test
    fun ac8_add_nineteenShortcuts_stillAccepted() {
        val c = HomeConfig(shortcuts = List(19) { Shortcut("s$it", "S$it", "https://s$it.example/") })
        assertEquals(
            20,
            HomeConfigEditor
                .add(c, "", "example.com", ::nextId)
                .ok()
                .shortcuts.size,
        )
    }

    @Test
    fun ac8_add_longTitle_isCutToFortyChars() {
        val title = "x".repeat(60)
        val c = HomeConfigEditor.add(base, title, "example.com", ::nextId).ok()
        assertEquals("x".repeat(40), c.shortcuts.last().title)
    }

    // --- AC9: update ---

    @Test
    fun ac9_update_changesTitleAndUrl_keepsIdAndPosition() {
        val c = HomeConfigEditor.update(base, "b", "New", "example.com").ok()
        assertEquals(listOf("a", "b", "c", "d"), c.ids())
        assertEquals(Shortcut("b", "New", "https://example.com"), c.shortcuts[1])
    }

    @Test
    fun ac9_update_urlOfAnotherShortcut_isDuplicate() {
        assertSame(ShortcutEdit.Duplicate, HomeConfigEditor.update(base, "b", "", sa.url))
    }

    @Test
    fun ac9_update_sameUrlAsItself_isOk() {
        val c = HomeConfigEditor.update(base, "b", "B2", sb.url).ok()
        assertEquals(Shortcut("b", "B2", sb.url), c.shortcuts[1])
    }

    @Test
    fun ac9_update_unknownId_isInvalid() {
        assertSame(ShortcutEdit.Invalid, HomeConfigEditor.update(base, "zzz", "T", "example.com"))
    }

    @Test
    fun ac9_update_invalidUrl_isInvalid() {
        assertSame(ShortcutEdit.Invalid, HomeConfigEditor.update(base, "b", "T", "javascript:alert(1)"))
    }

    // --- AC9: remove ---

    @Test
    fun ac9_remove_removesById() {
        assertEquals(listOf("a", "c", "d"), HomeConfigEditor.remove(base, "b").ids())
    }

    @Test
    fun ac9_remove_unknownId_leavesConfigUnchanged() {
        assertEquals(base, HomeConfigEditor.remove(base, "zzz"))
    }

    // --- AC9: move ---

    @Test
    fun ac9_move_forward() {
        assertEquals(listOf("b", "c", "a", "d"), HomeConfigEditor.move(base, 0, 2).ids())
    }

    @Test
    fun ac9_move_backward() {
        assertEquals(listOf("d", "a", "b", "c"), HomeConfigEditor.move(base, 3, 0).ids())
    }

    @Test
    fun ac9_move_outOfBounds_isClamped() {
        assertEquals(listOf("b", "c", "d", "a"), HomeConfigEditor.move(base, 0, 99).ids())
        assertEquals(listOf("d", "a", "b", "c"), HomeConfigEditor.move(base, 99, -5).ids())
    }

    @Test
    fun ac9_move_sameIndex_isUnchanged() {
        assertEquals(base, HomeConfigEditor.move(base, 2, 2))
    }

    // --- AC9: setBackground ---

    @Test
    fun ac9_setBackground_clampsDim() {
        assertEquals(
            HomeBackground.Image("f.jpg", 0.8f),
            HomeConfigEditor.setBackground(base, HomeBackground.Image("f.jpg", 1.5f)).background,
        )
        assertEquals(
            HomeBackground.Image("f.jpg", 0f),
            HomeConfigEditor.setBackground(base, HomeBackground.Image("f.jpg", -1f)).background,
        )
        assertEquals(
            HomeBackground.Image("f.jpg", 0.4f),
            HomeConfigEditor.setBackground(base, HomeBackground.Image("f.jpg", 0.4f)).background,
        )
    }

    @Test
    fun ac9_setBackground_keepsShortcuts() {
        val c = HomeConfigEditor.setBackground(base, HomeBackground.Solid(0xFF112233.toInt()))
        assertEquals(HomeBackground.Solid(0xFF112233.toInt()), c.background)
        assertEquals(base.shortcuts, c.shortcuts)
    }
}
