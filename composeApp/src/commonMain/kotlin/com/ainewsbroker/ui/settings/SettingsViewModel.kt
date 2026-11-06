package com.ainewsbroker.ui.settings

import androidx.lifecycle.ViewModel
import com.ainewsbroker.data.NewsRepository
import com.ainewsbroker.model.SearchQuery
import com.ainewsbroker.model.ThemeMode

class SettingsViewModel(private val repo: NewsRepository) : ViewModel() {
    val queries = repo.queries
    val settings = repo.settings
    val categories = repo.categories

    fun setTheme(mode: ThemeMode) = repo.updateSettings { it.copy(themeMode = mode) }

    fun setRefreshHours(hours: Int) = repo.updateSettings { it.copy(refreshHours = hours) }

    fun setEnabled(id: String, enabled: Boolean) = repo.setQueryEnabled(id, enabled)

    fun save(query: SearchQuery) = repo.upsertQuery(query)

    fun delete(id: String) = repo.deleteQuery(id)

    fun resetDefaults() = repo.resetQueriesToDefaults()

    fun newQuery(categoryId: String) = SearchQuery(id = repo.newQueryId(), categoryId = categoryId, label = "", rawQuery = "")
}
