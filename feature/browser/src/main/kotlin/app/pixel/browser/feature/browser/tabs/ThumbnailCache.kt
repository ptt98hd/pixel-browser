package app.pixel.browser.feature.browser.tabs

/**
 * Cache LRU theo dung lượng (spec 009 AC5), thuần JVM. [sizeOf] trả số byte của một giá trị.
 */
class ThumbnailCache<K : Any, V : Any>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Int,
) {
    private val map = LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    var sizeBytes: Long = 0
        private set

    operator fun get(key: K): V? = map[key]

    /** false (và không đẩy gì ra) nếu riêng [value] đã vượt `maxBytes`. */
    fun put(
        key: K,
        value: V,
    ): Boolean {
        val size = sizeOf(value).toLong()
        if (size > maxBytes) return false
        map.remove(key)?.let { sizeBytes -= sizeOf(it) }
        map[key] = value
        sizeBytes += size
        val it = map.entries.iterator()
        while (sizeBytes > maxBytes && it.hasNext()) {
            val eldest = it.next()
            if (eldest.key == key) break
            sizeBytes -= sizeOf(eldest.value)
            it.remove()
        }
        return true
    }

    fun remove(key: K) {
        map.remove(key)?.let { sizeBytes -= sizeOf(it) }
    }

    fun clear() {
        map.clear()
        sizeBytes = 0
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
