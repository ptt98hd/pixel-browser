package app.pixel.browser.core.engine

// Logic dùng chung giữa WebViewEngineSession và engine giả cho các request nền tảng web (spec 007),
// để hai engine tuân thủ cùng một hợp đồng.

/**
 * Bảng response hợp lệ theo loại request (spec 007 AC1). Engine có thể siết thêm (vd. WebView chỉ nhận
 * [EngineResponse.AttachWindow] với target là WebView).
 */
fun EngineRequest.accepts(response: EngineResponse): Boolean =
    when (this) {
        is EngineRequest.ExternalApp -> {
            response is EngineResponse.Dismiss
        }

        is EngineRequest.JsDialog -> {
            response is EngineResponse.Confirm ||
                response is EngineResponse.Dismiss ||
                (kind == JsDialogKind.Prompt && response is EngineResponse.PromptText)
        }

        is EngineRequest.PermissionPrompt -> {
            response is EngineResponse.Dismiss ||
                (response is EngineResponse.Grant && permissions.containsAll(response.permissions))
        }

        is EngineRequest.FileChooser -> {
            response is EngineResponse.Dismiss ||
                (response is EngineResponse.Files && (allowMultiple || response.uris.size <= 1))
        }

        is EngineRequest.HttpAuth -> {
            response is EngineResponse.Dismiss || response is EngineResponse.Credentials
        }

        is EngineRequest.NewWindow -> {
            response is EngineResponse.Dismiss || response is EngineResponse.AttachWindow
        }

        is EngineRequest.PopupBlocked,
        is EngineRequest.CloseWindow,
        is EngineRequest.Download,
        -> {
            response is EngineResponse.Dismiss
        }
    }

/** `accept` của `<input type=file>`: trim, chữ thường, bỏ chuỗi rỗng; tách cả danh sách phân cách bằng dấu phẩy. */
fun normalizeAcceptTypes(acceptTypes: List<String>): List<String> =
    acceptTypes
        .flatMap { it.split(',') }
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

/** Cờ "chặn dialog" của trang hiện tại (spec 007 AC3). Reset ở mỗi điều hướng main frame mới. */
class JsDialogGuard {
    private var page = 0L
    private var shown = 0
    private var suppressed = false

    fun onNewPage() {
        page++
        shown = 0
        suppressed = false
    }

    internal val isSuppressed: Boolean get() = suppressed

    /** Ghi nhận một dialog mới; trả (trang hiện tại, canSuppress). */
    internal fun next(): Pair<Long, Boolean> = page to (shown++ > 0)

    /** Chỉ chặn nếu response đến khi vẫn còn ở trang đã tạo dialog. */
    internal fun suppress(forPage: Long) {
        if (forPage == page) suppressed = true
    }
}

private val EngineResponse.suppressFurther: Boolean
    get() =
        when (this) {
            is EngineResponse.Confirm -> suppressFurther
            is EngineResponse.Dismiss -> suppressFurther
            is EngineResponse.PromptText -> suppressFurther
            else -> false
        }

/**
 * Đưa JS dialog vào hàng đợi, hoặc tự trả lời nếu trang đã bị chặn dialog (spec 007 AC3): Alert/Confirm/Prompt
 * → `Dismiss()`, BeforeUnload → `Confirm()` (không nhốt người dùng). [onAutoResponse] nhận response tự động;
 * [deliver] nhận response hợp lệ của người dùng. Trả null nếu dialog bị chặn.
 */
fun EngineRequestQueue.enqueueJsDialog(
    guard: JsDialogGuard,
    kind: JsDialogKind,
    origin: String,
    message: String,
    defaultValue: String?,
    onAutoResponse: (EngineResponse) -> Unit,
    deliver: (EngineResponse) -> Unit,
): EngineRequest.JsDialog? {
    if (guard.isSuppressed) {
        onAutoResponse(if (kind == JsDialogKind.BeforeUnload) EngineResponse.Confirm() else EngineResponse.Dismiss())
        return null
    }
    val (page, canSuppress) = guard.next()
    return enqueue(
        build = { id ->
            EngineRequest.JsDialog(
                id = id,
                origin = origin,
                kind = kind,
                message = message,
                defaultValue = defaultValue.takeIf { kind == JsDialogKind.Prompt },
                canSuppress = canSuppress,
            )
        },
        onResponse = { request, response ->
            if (!request.accepts(response)) return@enqueue false
            if (request.canSuppress && response.suppressFurther) guard.suppress(page)
            deliver(response)
            true
        },
    )
}
