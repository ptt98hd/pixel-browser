package app.pixel.browser.feature.home

import app.pixel.browser.core.data.home.HomeConfig
import app.pixel.browser.core.data.home.HomeConfigStore

/** Nhật ký chung để kiểm thứ tự ghi config / xóa ảnh. */
class EventLog {
    val events = mutableListOf<String>()
}

/** [HomeConfigStore] trong bộ nhớ, ghi lại mọi lần ghi và thread đọc/ghi. */
class FakeHomeConfigStore(
    var stored: HomeConfig? = null,
    private val log: EventLog = EventLog(),
) : HomeConfigStore {
    val writes = mutableListOf<HomeConfig>()
    var reads = 0
        private set

    override fun read(): HomeConfig? {
        reads++
        return stored
    }

    override fun write(config: HomeConfig) {
        writes += config
        stored = config
        log.events += "write"
    }
}

/** [BackgroundImageImporter] giả: trả [result] cho mọi URI, [existing] là các file "có trên đĩa". */
class FakeImageImporter(
    var result: String? = "bg-1.jpg",
    val existing: MutableSet<String> = mutableSetOf(),
    private val log: EventLog = EventLog(),
) : BackgroundImageImporter {
    val imported = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override suspend fun import(uri: String): String? {
        imported += uri
        log.events += "import:$uri"
        return result?.also { existing += it }
    }

    override fun exists(fileName: String): Boolean = fileName in existing

    override fun delete(fileName: String) {
        deleted += fileName
        existing -= fileName
        log.events += "delete:$fileName"
    }
}
