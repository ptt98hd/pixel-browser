package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.EngineSession

/** Tạo [EngineSession] mới cho một tab; được inject để test dùng `FakeEngineSession`. */
fun interface EngineSessionFactory {
    fun create(): EngineSession
}
