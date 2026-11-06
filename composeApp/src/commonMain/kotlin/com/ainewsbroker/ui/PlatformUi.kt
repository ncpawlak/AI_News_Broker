package com.ainewsbroker.ui

import androidx.compose.runtime.Composable

/** Opens an article: Chrome Custom Tabs on Android, a new browser tab on web. */
@Composable
expect fun rememberUrlOpener(): (String) -> Unit

/** Pull-to-refresh needs touch; mouse-wheel overscroll on desktop web leaves the indicator stuck. */
expect val supportsPullToRefresh: Boolean

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
