package dev.snapttl.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.snapttl.SnapTtlApp

/**
 * Indexes unindexed device screenshots with on-device OCR + image labeling.
 * Clean-room equivalent of Mark's `ScreenshotIndexWorker`.
 */
class OcrIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SnapTtlApp
        val summary = app.services.searchRepo.indexAll()
        setProgress(workDataOf(KEY_INDEXED to summary.indexed, KEY_TOTAL to summary.total))
        return Result.success()
    }

    companion object {
        const val KEY_INDEXED = "indexed"
        const val KEY_TOTAL = "total"
    }
}
