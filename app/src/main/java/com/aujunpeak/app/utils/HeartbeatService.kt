package com.aujunpeak.app.utils

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class HeartbeatWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        if (!KeyManager.isLoggedIn(ctx)) return@withContext Result.success()
        // WorkManager chạy nền mỗi 15 phút (minimum của WorkManager)
        // Trong khi app đang mở, MainActivity sẽ tự heartbeat mỗi 2 phút
        KeyManager.sendHeartbeat(ctx)
        Result.success()
    }
}

object HeartbeatScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "aujunpeak_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("aujunpeak_heartbeat")
    }
}
