package com.ainewsbroker.ui

import androidx.compose.runtime.Composable
import kotlinx.browser.window

@Composable
actual fun rememberUrlOpener(): (String) -> Unit = { url -> window.open(url, "_blank", "noopener") }

actual val supportsPullToRefresh: Boolean = false

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
