package com.ainewsbroker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ainewsbroker.platform.scheduleBackgroundRefresh
import com.ainewsbroker.ui.PlatformBackHandler
import com.ainewsbroker.ui.feed.FeedScreen
import com.ainewsbroker.ui.feed.FeedViewModel
import com.ainewsbroker.ui.settings.SettingsScreen
import com.ainewsbroker.ui.settings.SettingsViewModel
import com.ainewsbroker.ui.theme.AppTheme
import kotlinx.coroutines.delay

private enum class Screen { Feed, Settings }

@Composable
fun App() {
    val repo = AppGraph.repository
    val settings by repo.settings.collectAsStateWithLifecycle()
    val feedViewModel = viewModel { FeedViewModel(repo) }
    val settingsViewModel = viewModel { SettingsViewModel(repo) }
    var screen by rememberSaveable { mutableStateOf(Screen.Feed) }

    LaunchedEffect(settings.refreshHours) {
        scheduleBackgroundRefresh(settings.refreshHours)
        // Foreground timer: keeps the feed fresh while the app/tab stays open.
        while (true) {
            delay(settings.refreshHours * 3_600_000L)
            feedViewModel.refresh()
        }
    }

    AppTheme(settings.themeMode) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PlatformBackHandler(enabled = screen != Screen.Feed) { screen = Screen.Feed }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState == Screen.Settings
                    (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(240)))
                        .togetherWith(slideOutHorizontally(tween(320)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(180)))
                },
                label = "screen",
            ) { target ->
                when (target) {
                    Screen.Feed -> FeedScreen(feedViewModel, onOpenSettings = { screen = Screen.Settings })
                    Screen.Settings -> SettingsScreen(settingsViewModel, onBack = { screen = Screen.Feed })
                }
            }
        }
    }
}
