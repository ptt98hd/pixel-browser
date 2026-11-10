package app.pixel.browser.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 016 – `FakeEngineSession(isPrivate)` phản ánh cờ ẩn danh (JVM). */
class FakePrivateSessionTest {
    @Test
    fun defaultFake_isNotPrivate() = assertFalse(FakeEngineSession().isPrivate)

    @Test
    fun privateFake_isPrivate() = assertTrue(FakeEngineSession(isPrivate = true).isPrivate)
}
