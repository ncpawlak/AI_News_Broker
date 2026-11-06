package com.ainewsbroker.platform

import io.ktor.client.HttpClient

/** Simple string key-value persistence (files on Android, localStorage on web). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

expect fun createKeyValueStore(): KeyValueStore

expect fun createHttpClient(): HttpClient

/** Base URL for Google News; the web build goes through the dev-server proxy. */
expect val newsBaseUrl: String

/** Schedules OS-level background refresh where supported (WorkManager on Android). */
expect fun scheduleBackgroundRefresh(hours: Int)

expect val supportsBackgroundRefresh: Boolean
