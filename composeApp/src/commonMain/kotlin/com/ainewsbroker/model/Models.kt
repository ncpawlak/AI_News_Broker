package com.ainewsbroker.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String,
    val name: String,
)

@Serializable
data class SearchQuery(
    val id: String,
    val categoryId: String,
    val label: String,
    val rawQuery: String,
    val enabled: Boolean = true,
)

@Serializable
data class Article(
    val id: String,
    val title: String,
    val source: String,
    val link: String,
    val publishedAtMillis: Long,
    val categoryId: String,
    val queryId: String,
    val queryLabel: String,
    val fetchedAtMillis: Long,
)

@Serializable
enum class ThemeMode { System, Light, Dark }

@Serializable
data class AppSettings(
    val refreshHours: Int = 3,
    val themeMode: ThemeMode = ThemeMode.System,
)

val RefreshIntervalOptions = listOf(1, 3, 6, 12)
