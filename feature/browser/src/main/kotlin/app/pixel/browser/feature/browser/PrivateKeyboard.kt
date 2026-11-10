package app.pixel.browser.feature.browser

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import app.pixel.browser.core.engine.applyPrivateKeyboard

/**
 * Khi [enabled] (tab ẩn danh đang chọn), mọi ô nhập trong [content] (thanh URL, thanh tìm, ô đăng nhập HTTP, kể cả
 * hộp thoại vì chúng dùng chung composition) yêu cầu bàn phím không học từ nội dung và tắt gợi ý/tự sửa
 * (`IME_FLAG_NO_PERSONALIZED_LEARNING` + `TYPE_TEXT_FLAG_NO_SUGGESTIONS`; spec 016). Chỉ là đề nghị: bàn phím bên
 * thứ ba có thể bỏ qua. Không test được bằng Robolectric (cần IME thật); phần tính cờ được test ở `:core:engine`.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivateKeyboard(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    InterceptPlatformTextInput(
        interceptor = { request, session ->
            session.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttrs: EditorInfo): InputConnection =
                        request.createInputConnection(outAttrs).also { applyPrivateKeyboard(outAttrs) }
                },
            )
        },
        content = content,
    )
}
