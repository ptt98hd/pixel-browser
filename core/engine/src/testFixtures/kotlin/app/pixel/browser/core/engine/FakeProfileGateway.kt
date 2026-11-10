package app.pixel.browser.core.engine

import android.webkit.WebView

/**
 * [ProfileGateway] giả cho test (spec 016): [names] là các profile đang tồn tại (mặc định chỉ có `Default`),
 * [appliedProfiles]/[deleted] ghi lại lời gọi, [calls] ghi mọi lời gọi theo thứ tự (`list`, `apply:<tên>`,
 * `delete:<tên>`).
 *
 * - [failDeleteTimes] > 0: mỗi lần [delete] trả `false` (profile còn đang dùng) và giảm một.
 * - [deleteThrows] khác null: [delete] ném lỗi đó.
 * - [applyFailure] khác null: [apply] ném lỗi đó (sau khi gọi [onApply]).
 * - [onApply] chạy ngay khi [apply] được gọi (để test kiểm trạng thái WebView tại thời điểm đó).
 */
class FakeProfileGateway(
    supported: Boolean = true,
    names: Collection<String> = listOf(DEFAULT_PROFILE),
) : ProfileGateway {
    override val isSupported: Boolean = supported

    val names: MutableList<String> = names.toMutableList()
    val appliedProfiles = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val calls = mutableListOf<String>()

    var failDeleteTimes = 0
    var deleteThrows: RuntimeException? = null
    var applyFailure: RuntimeException? = null
    var onApply: ((WebView, String) -> Unit)? = null

    override fun listProfileNames(): List<String> {
        calls += "list"
        return names.toList()
    }

    override fun apply(
        webView: WebView,
        profileName: String,
    ) {
        calls += "apply:$profileName"
        appliedProfiles += profileName
        if (profileName !in names) names += profileName
        onApply?.invoke(webView, profileName)
        applyFailure?.let { throw it }
    }

    override fun delete(profileName: String): Boolean {
        calls += "delete:$profileName"
        deleted += profileName
        deleteThrows?.let { throw it }
        if (failDeleteTimes > 0) {
            failDeleteTimes--
            return false
        }
        names.remove(profileName)
        return true
    }

    companion object {
        const val DEFAULT_PROFILE = "Default"
    }
}
