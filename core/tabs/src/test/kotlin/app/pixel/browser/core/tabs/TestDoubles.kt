package app.pixel.browser.core.tabs

import app.pixel.browser.core.engine.FakeEngineSession

/** Factory ghi lại mọi session đã tạo để kiểm tra không recreate / đã đóng. */
class RecordingSessionFactory : EngineSessionFactory {
    val created = mutableListOf<FakeEngineSession>()

    override fun create(): FakeEngineSession = FakeEngineSession().also { created += it }
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
