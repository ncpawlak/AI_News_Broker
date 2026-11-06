package com.ainewsbroker.platform

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.content.Context
import com.ainewsbroker.AppGraph
import com.ainewsbroker.NewsApplication
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import java.io.File
import java.util.concurrent.TimeUnit

private class FileKeyValueStore(private val dir: File) : KeyValueStore {
    init { dir.mkdirs() }

    override fun get(key: String): String? = File(dir, "$key.json").takeIf { it.exists() }?.readText()

    @Synchronized
    override fun put(key: String, value: String) {
        val target = File(dir, "$key.json")
        val tmp = File(dir, "$key.json.tmp")
        tmp.writeText(value)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }
}

actual fun createKeyValueStore(): KeyValueStore = FileKeyValueStore(File(NewsApplication.appContext.filesDir, "store"))

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
    }
    defaultRequest {
        // Bing returns HTML instead of RSS for mobile/Android User-Agents.
        headers.append(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; AINewsBroker/0.1)")
    }
}

actual fun platformUrl(url: String): String = url

actual val supportsBackgroundRefresh: Boolean = true

actual fun scheduleBackgroundRefresh(hours: Int) {
    val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(NewsApplication.appContext)
        .enqueueUniquePeriodicWork("news-refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { AppGraph.repository.refreshAll() }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 3) Result.retry() else Result.failure() },
        )
}
