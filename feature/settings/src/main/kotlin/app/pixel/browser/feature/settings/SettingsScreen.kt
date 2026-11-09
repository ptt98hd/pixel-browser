package app.pixel.browser.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Khung một màn hình cài đặt: thanh trên (Back + tiêu đề) và nội dung, tránh vùng hệ thống. */
@Composable
private fun SettingsFrame(
    title: String,
    rootTag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxSize().testTag(rootTag), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag(SettingsTestTags.BACK)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .testTag(SettingsTestTags.TITLE)
                            .semantics { heading() },
                )
            }
            Column(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Danh sách mục Cài đặt (spec 014 AC11), dựng từ [registry]. */
@Composable
fun SettingsScreen(
    registry: SettingsRegistry,
    onOpenPage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsFrame(
        title = stringResource(R.string.settings_title),
        rootTag = SettingsTestTags.SCREEN,
        onBack = onClose,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            registry.groups.forEach { (section, pages) ->
                Text(
                    text = section.title(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag(SettingsTestTags.section(section.id))
                            .semantics { heading() },
                )
                pages.forEach { page -> PageRow(page, onClick = { onOpenPage(page.id) }) }
            }
        }
    }
}

@Composable
private fun PageRow(
    page: SettingsPage,
    onClick: () -> Unit,
) {
    val title = page.title()
    val summary = if (page.isPlaceholder) stringResource(R.string.settings_coming_soon) else page.summary?.invoke()
    val base = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val modifier =
        if (page.isPlaceholder) {
            base
                .testTag(SettingsTestTags.placeholder(page.id))
                .semantics(mergeDescendants = true) { disabled() }
        } else {
            base
                .testTag(SettingsTestTags.item(page.id))
                .clickable(role = Role.Button, onClick = onClick)
        }
    Column(
        modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val alpha = if (page.isPlaceholder) 0.6f else 1f
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val StackSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

/**
 * Điều hướng trong Cài đặt bằng ngăn xếp id trang (spec 014 AC12): gốc là danh sách mục; Back lùi một cấp,
 * ở gốc thì [onClose]. Ngăn xếp giữ qua xoay màn hình.
 */
@Composable
fun SettingsRoute(
    registry: SettingsRegistry,
    startPageId: String? = null,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var stack by rememberSaveable(stateSaver = StackSaver) {
        mutableStateOf(listOfNotNull(startPageId?.takeIf { registry.page(it)?.isPlaceholder == false }))
    }
    // Trang đã biến mất/giữ chỗ (registry đổi giữa các lần chạy) → coi như không có trong ngăn xếp.
    val validStack = stack.filter { registry.page(it)?.isPlaceholder == false }
    val currentPage = validStack.lastOrNull()?.let(registry::page)
    val latestStack by rememberUpdatedState(validStack)
    val latestClose by rememberUpdatedState(onClose)
    val latestOpenUrl by rememberUpdatedState(onOpenUrl)
    val pop: () -> Unit = {
        if (latestStack.isEmpty()) latestClose() else stack = latestStack.dropLast(1)
    }
    val push: (String) -> Unit = { id ->
        if (registry.page(id)?.isPlaceholder == false) stack = latestStack + id
    }
    BackHandler(onBack = pop)
    val scope =
        remember(registry) {
            object : SettingsPageScope {
                override fun navigateBack() = pop()

                override fun open(pageId: String) = push(pageId)

                override fun openUrl(url: String) = latestOpenUrl(url)
            }
        }
    if (currentPage == null) {
        SettingsScreen(registry, onOpenPage = push, onClose = onClose)
    } else {
        SettingsFrame(
            title = currentPage.title(),
            rootTag = SettingsTestTags.page(currentPage.id),
            onBack = pop,
        ) { currentPage.content(scope) }
    }
}
