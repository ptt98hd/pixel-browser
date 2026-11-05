package app.pixel.browser.feature.browser

import app.pixel.browser.core.omnibox.Suggestion

/** Callback hành động của chrome (spec 005). Mặc định no-op để test composable độc lập. */
data class BrowserActions(
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onNewTab: () -> Unit = {},
    val onScrollToTop: () -> Unit = {},
    val onScrollToBottom: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onStartEditing: () -> Unit = {},
    val onCancelEditing: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onSuggestionClick: (Suggestion) -> Unit = {},
    val onSelectTab: (String) -> Unit = {},
    val onCloseTab: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)
