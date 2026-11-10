package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.FakeEngineSession

/** Factory ghi lại mọi session đã tạo để kiểm tra không recreate / đã đóng. */
class RecordingSessionFactory : EngineSessionFactory {
    val created = mutableListOf<FakeEngineSession>()

    /** Giá trị `isPrivate` của từng lời gọi [create], theo thứ tự (spec 016). */
    val requestedPrivate = mutableListOf<Boolean>()

    override fun create(isPrivate: Boolean): FakeEngineSession =
        FakeEngineSession(isPrivate).also {
            created += it
            requestedPrivate += isPrivate
        }
}

class InMemoryTabSnapshotStore(
    var stored: TabsSnapshot? = null,
    private val failOnRead: Boolean = false,
) : TabSnapshotStore {
    var writes = 0
        private set

    override fun read(): TabsSnapshot? {
        if (failOnRead) throw IllegalStateException("corrupt snapshot")
        return stored
    }

    override fun write(snapshot: TabsSnapshot) {
        writes++
        stored = snapshot
    }
}
