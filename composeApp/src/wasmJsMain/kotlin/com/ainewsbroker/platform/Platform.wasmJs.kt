package com.ainewsbroker.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import kotlinx.browser.localStorage

private object LocalStorageStore : KeyValueStore {
    private const val PREFIX = "ainews."

    override fun get(key: String): String? = localStorage.getItem(PREFIX + key)

    override fun put(key: String, value: String) {
        runCatching { localStorage.setItem(PREFIX + key, value) }
    }
}

actual fun createKeyValueStore(): KeyValueStore = LocalStorageStore

actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    install(HttpTimeout) { requestTimeoutMillis = 20_000 }
}

// Relative path served by the webpack dev-server proxy (see webpack.config.d/proxy.js).
actual val newsBaseUrl: String = "/gnews"

actual val supportsBackgroundRefresh: Boolean = false

actual fun scheduleBackgroundRefresh(hours: Int) = Unit
