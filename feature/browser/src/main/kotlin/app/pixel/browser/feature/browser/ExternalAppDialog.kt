package app.pixel.browser.feature.browser

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.pixel.browser.core.engine.EngineRequest
import app.pixel.browser.feature.browser.intent.ExternalIntents

/**
 * Hỏi người dùng trước khi mở app khác (spec 006 AC24). Nội dung lấy từ [ExternalIntents.describe] – cùng parse
 * mà `AndroidExternalAppLauncher` dùng để mở – nên package/scheme hiển thị luôn là thứ sẽ được mở. URI bị từ
 * chối → không hiện, tự hủy. Back/chạm ngoài = hủy. Mọi lựa chọn đều trả lời request.
 */
@Composable
fun ExternalAppDialog(
    request: EngineRequest.ExternalApp,
    onDecision: (open: Boolean) -> Unit,
) {
    val ownPackage = LocalContext.current.packageName
    val target = remember(request.uri, ownPackage) { ExternalIntents.describe(request.uri, ownPackage) }
    if (target == null) {
        LaunchedEffect(request.id) { onDecision(false) }
        return
    }
    val text =
        if (target.packageName != null) {
            stringResource(R.string.browser_external_message_package, target.packageName)
        } else {
            stringResource(R.string.browser_external_message_scheme, target.scheme)
        }
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        modifier = Modifier.testTag(BrowserTestTags.EXTERNAL_DIALOG),
        title = { Text(stringResource(R.string.browser_external_title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = { onDecision(true) },
                modifier = Modifier.testTag(BrowserTestTags.EXTERNAL_OPEN),
            ) { Text(stringResource(R.string.browser_external_open)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onDecision(false) },
                modifier = Modifier.testTag(BrowserTestTags.EXTERNAL_CANCEL),
            ) { Text(stringResource(R.string.browser_external_cancel)) }
        },
    )
}
