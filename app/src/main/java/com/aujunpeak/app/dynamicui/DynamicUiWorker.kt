package com.aujunpeak.app.dynamicui

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DynamicUiWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // A missing/unreachable UI server must never create an aggressive retry
        // loop. The app keeps its last known-good cache and the periodic job will
        // check again on the next scheduled interval.
        DynamicUiManager.refreshIfNeeded(applicationContext, force = true)
        return Result.success()
    }
}
