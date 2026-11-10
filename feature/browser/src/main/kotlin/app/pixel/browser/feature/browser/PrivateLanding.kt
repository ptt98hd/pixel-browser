package app.pixel.browser.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Trang chủ của tab ẩn danh (spec 016 AC18): giải thích + bảng bảo đảm + ô tìm kiếm. Không hiện shortcut hay cấu
 * hình trang chủ của người dùng. Chạm ô tìm kiếm → [onSearchClick] (sửa URL).
 */
@Composable
fun PrivateLanding(
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(BrowserTestTags.PRIVATE_LANDING),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                PrivateIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(stringResource(R.string.browser_private_landing_title), style = MaterialTheme.typography.headlineSmall)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClick = onSearchClick)
                        .testTag(BrowserTestTags.PRIVATE_LANDING_SEARCH),
            ) {
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        stringResource(R.string.browser_private_landing_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.browser_private_landing_body), style = MaterialTheme.typography.bodyMedium)
            Guarantee(R.string.browser_private_guaranteed_title, R.string.browser_private_guaranteed)
            Guarantee(R.string.browser_private_not_guaranteed_title, R.string.browser_private_not_guaranteed)
        }
    }
}

@Composable
private fun Guarantee(
    title: Int,
    body: Int,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
