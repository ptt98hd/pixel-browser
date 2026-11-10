package app.pixel.browser.core.data.site

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Chính sách desktop hai tầng: mặc định toàn cục + ngoại lệ theo site (spec 015 B05). Dùng trên main thread. */
class DesktopSitePolicy(
    initial: DesktopSiteData,
    private val persist: (DesktopSiteData) -> Unit,
) {
    // Giữ thứ tự chèn; đặt lại một site đưa nó về cuối (LRU theo lần đặt).
    private val sites = LinkedHashMap<String, Boolean>(initial.sites)
    private val _defaultDesktop = MutableStateFlow(initial.defaultDesktop)
    private val _overrideCount = MutableStateFlow(sites.size)

    val defaultDesktop: StateFlow<Boolean> = _defaultDesktop

    val overrideCount: StateFlow<Int> = _overrideCount

    /** Ngoại lệ site ?: mặc định. */
    fun resolve(siteKey: String?): Boolean = siteKey?.let(sites::get) ?: _defaultDesktop.value

    fun overrideFor(siteKey: String): Boolean? = sites[siteKey]

    /** Bằng mặc định → xóa mục; lưu qua `persist`. */
    fun setSite(
        siteKey: String,
        desktop: Boolean,
    ) {
        if (siteKey.isBlank()) return
        if (desktop == _defaultDesktop.value) {
            if (sites.remove(siteKey) == null) return
        } else {
            sites.remove(siteKey)
            sites[siteKey] = desktop
            while (sites.size > MAX_SITES) sites.remove(sites.keys.first())
        }
        commit()
    }

    fun setDefault(desktop: Boolean) {
        if (_defaultDesktop.value == desktop) return
        _defaultDesktop.value = desktop
        commit()
    }

    fun clearOverrides() {
        sites.clear()
        commit()
    }

    private fun commit() {
        _overrideCount.value = sites.size
        persist(DesktopSiteData(defaultDesktop = _defaultDesktop.value, sites = LinkedHashMap(sites)))
    }

    companion object {
        const val MAX_SITES = 1000
    }
}
