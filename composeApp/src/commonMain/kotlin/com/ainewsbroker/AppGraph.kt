package com.ainewsbroker

import com.ainewsbroker.data.NewsRepository
import com.ainewsbroker.platform.createHttpClient
import com.ainewsbroker.platform.createKeyValueStore
import com.ainewsbroker.platform.newsBaseUrl

/** Manual dependency graph; the repository is a process-wide singleton shared with background workers. */
object AppGraph {
    val repository: NewsRepository by lazy {
        NewsRepository(createKeyValueStore(), createHttpClient(), newsBaseUrl)
    }
}
