package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.EngineSession

/**
 * Tạo [EngineSession] mới cho một tab; được inject để test dùng `FakeEngineSession`.
 * [isPrivate]: session của tab ẩn danh (profile riêng, spec 016); có thể ném `PrivateUnavailableException`.
 */
fun interface EngineSessionFactory {
    fun create(isPrivate: Boolean): EngineSession
}
