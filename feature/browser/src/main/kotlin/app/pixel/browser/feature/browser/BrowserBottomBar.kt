package app.pixel.browser.feature.browser

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pixel.browser.core.ui.PixelIconButton
import app.pixel.browser.feature.browser.gesture.tabSwipe

/** Số tab tối đa hiển thị trên badge; lớn hơn thì hiện `99+` (AC17). */
private const val MAX_BADGE_COUNT = 99
private const val DISABLED_ALPHA = 0.38f

/** Nhãn badge số tab: `1`…`99`, rồi `99+`. */
internal fun tabCountBadge(count: Int): String = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else "$count"

/**
 * Thanh dưới (A06/A07): back / forward / home / tabs / menu.
 * Tap: back → [BrowserActions.onBack], forward → onForward, home → onHome, tabs → [onTabsClick], menu → [onMenuClick].
 * Long-press (kể cả khi back/forward disabled): back → onScrollToTop, forward → onScrollToBottom,
 * home → onStartEditing, tabs → onNewTab, menu → onReload.
 */
@Composable
fun BrowserBottomBar(
    state: BrowserUiState,
    actions: BrowserActions,
    onTabsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Spec 008 AC12: vuốt ngang trên thanh để đổi tab kề (do `GestureGate` quyết định). */
    tabSwipeEnabled: Boolean = true,
) {
    val tabIndex = state.tabs.indexOfFirst { it.id == state.selectedTabId }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previousLabel = stringResource(R.string.browser_previous_tab)
    val nextLabel = stringResource(R.string.browser_next_tab)
    val tabActions =
        buildList {
            if (tabIndex > 0) {
                add(
                    CustomAccessibilityAction(previousLabel) {
                        actions.onSelectAdjacentTab(-1)
                        true
                    },
                )
            }
            if (tabIndex in 0 until state.tabs.lastIndex) {
                add(
                    CustomAccessibilityAction(nextLabel) {
                        actions.onSelectAdjacentTab(+1)
                        true
                    },
                )
            }
        }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(BrowserTestTags.BOTTOM_BAR)
                .tabSwipe(tabSwipeEnabled, tabIndex, state.tabs.size, rtl, actions.onSelectAdjacentTab),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeButton(
                tag = BrowserTestTags.BTN_BACK,
                description = stringResource(R.string.browser_back),
                longPressLabel = stringResource(R.string.browser_scroll_top),
                enabled = state.canGoBack,
                onClick = actions.onBack,
                onLongClick = actions.onScrollToTop,
            ) { ChromeIcon(Icons.AutoMirrored.Filled.ArrowBack) }
            ChromeButton(
                tag = BrowserTestTags.BTN_FORWARD,
                description = stringResource(R.string.browser_forward),
                longPressLabel = stringResource(R.string.browser_scroll_bottom),
                enabled = state.canGoForward,
                onClick = actions.onForward,
                onLongClick = actions.onScrollToBottom,
            ) { ChromeIcon(Icons.AutoMirrored.Filled.ArrowForward) }
            ChromeButton(
                tag = BrowserTestTags.BTN_HOME,
                description = stringResource(R.string.browser_home),
                longPressLabel = stringResource(R.string.browser_edit_url),
                onClick = actions.onHome,
                onLongClick = actions.onStartEditing,
            ) { ChromeIcon(Icons.Filled.Home) }
            ChromeButton(
                tag = BrowserTestTags.BTN_TABS,
                description = pluralStringResource(R.plurals.browser_tabs, state.tabCount, state.tabCount),
                longPressLabel = stringResource(R.string.browser_new_tab),
                onClick = onTabsClick,
                onLongClick = actions.onNewTab,
                extraActions = tabActions,
            ) { TabCountBadge(state.tabCount) }
            ChromeButton(
                tag = BrowserTestTags.BTN_MENU,
                description = stringResource(R.string.browser_menu),
                longPressLabel = stringResource(R.string.browser_reload),
                onClick = onMenuClick,
                onLongClick = actions.onReload,
            ) { ChromeIcon(Icons.Filled.MoreVert) }
        }
    }
}

/**
 * Nút của bottom bar trên [PixelIconButton] (đổi hình khi nhấn, spec 011 AC9). [enabled] chỉ áp dụng cho tap
 * (semantics `disabled()`, tap no-op); long-press và custom accessibility action luôn hoạt động (spec 005,
 * Edge cases / AC23).
 */
@Composable
private fun RowScope.ChromeButton(
    tag: String,
    description: String,
    longPressLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean = true,
    extraActions: List<CustomAccessibilityAction> = emptyList(),
    content: @Composable () -> Unit,
) {
    PixelIconButton(
        onClick = onClick,
        enabled = enabled,
        onLongClick = onLongClick,
        onLongClickLabel = longPressLabel,
        modifier =
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .semantics {
                    contentDescription = description
                    if (!enabled) disabled()
                    customActions =
                        listOf(
                            CustomAccessibilityAction(longPressLabel) {
                                onLongClick()
                                true
                            },
                        ) + extraActions
                }.testTag(tag),
    ) {
        val color = LocalContentColor.current
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) color else color.copy(alpha = DISABLED_ALPHA),
            content = content,
        )
    }
}

@Composable
private fun ChromeIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null)
}

@Composable
private fun TabCountBadge(count: Int) {
    Box(
        modifier =
            Modifier
                .size(width = 26.dp, height = 24.dp)
                .border(2.dp, LocalContentColor.current, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tabCountBadge(count),
            fontSize = if (count > MAX_BADGE_COUNT) 9.sp else 12.sp,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag(BrowserTestTags.TAB_COUNT),
        )
    }
}
