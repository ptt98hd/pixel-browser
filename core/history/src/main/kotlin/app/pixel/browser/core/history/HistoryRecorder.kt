package app.pixel.browser.core.history

import app.pixel.browser.core.omnibox.UrlDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PageVisit(
    val tabId: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val isError: Boolean,
)

interface PageVisitRecorder {
    fun record(visit: PageVisit)

    fun forgetTab(tabId: String)

    object None : PageVisitRecorder {
        override fun record(visit: PageVisit) = Unit

        override fun forgetTab(tabId: String) = Unit
    }
}

class HistoryRecorder(
    private val store: HistoryStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : PageVisitRecorder {
    private class Last(
        val key: String,
        val id: Long,
        val title: String,
    )

    // Mọi việc chạy tuần tự qua chuỗi job để trạng thái dedupe không cần khóa và thứ tự ghi được giữ.
    private val lock = Any()
    private var tail: Job? = null
    private val lastByTab = HashMap<String, Last>()
    private var writes = 0

    override fun record(visit: PageVisit) {
        if (visit.isPrivate || visit.isError || !isRecordable(visit.url)) return
        val title = visit.title.take(HistoryRetention.MAX_TITLE)
        enqueue { write(visit.tabId, visit.url, title) }
    }

    override fun forgetTab(tabId: String) {
        enqueue { lastByTab.remove(tabId) }
    }

    private fun enqueue(block: suspend () -> Unit) {
        synchronized(lock) {
            val previous = tail
            tail =
                scope.launch {
                    previous?.join()
                    try {
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Lịch sử là tính năng phụ: lỗi lưu trữ không được làm sập hay chặn các lần ghi sau.
                    }
                }
        }
    }

    private suspend fun write(
        tabId: String,
        url: String,
        title: String,
    ) {
        val now = clock()
        val key = url.substringBefore('#')
        val last = lastByTab[tabId]
        if (last != null && last.key == key) {
            // Tiêu đề trống (chưa kịp nhận) không được ghi đè tiêu đề tốt đã lưu.
            val kept = title.ifBlank { last.title }
            if (store.touch(last.id, kept, now)) {
                lastByTab[tabId] = Last(key, last.id, kept)
                return
            }
        }
        val stored = title.ifBlank { UrlDisplay.host(url) }
        lastByTab[tabId] = Last(key, store.insert(url, stored, now), stored)
        if (++writes % PRUNE_EVERY == 0) {
            store.prune(HistoryRetention.MAX_ENTRIES, now - HistoryRetention.MAX_AGE_DAYS * DAY_MS)
        }
    }

    private fun isRecordable(url: String): Boolean {
        if (url.length > HistoryRetention.MAX_URL) return false
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0 || url.substring(0, schemeEnd).lowercase() !in WEB_SCHEMES) return false
        val rest = url.substring(schemeEnd + 3)
        val authority = rest.takeWhile { it !in "/?#\\" }
        return authority.isNotEmpty() && '@' !in authority
    }

    private companion object {
        const val PRUNE_EVERY = 100
        val WEB_SCHEMES = setOf("http", "https")
    }
}
