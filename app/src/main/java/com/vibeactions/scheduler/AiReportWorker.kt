package com.vibeactions.scheduler

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vibeactions.util.AiReportBody
import com.vibeactions.util.sendAiReport
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * Delivers an AI report that couldn't be sent from the dialog. The report backend's auth host is
 * IPv4-only, so on IPv6-only Wi-Fi the first attempt fails; retrying with backoff gets it through
 * once the phone is on a network that can reach it (e.g. mobile data).
 */
class AiReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val json = inputData.getString(KEY_REPORT) ?: return Result.failure()
        val report = runCatching { Json.decodeFromString(AiReportBody.serializer(), json) }
            .getOrElse { return Result.failure() }
        return if (runCatching { sendAiReport(report) }.isSuccess) Result.success()
        else if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
        else Result.failure()
    }

    companion object {
        private const val KEY_REPORT = "report"
        private const val MAX_ATTEMPTS = 20

        fun enqueue(context: Context, report: AiReportBody) {
            val request = OneTimeWorkRequestBuilder<AiReportWorker>()
                .setInputData(workDataOf(KEY_REPORT to Json.encodeToString(AiReportBody.serializer(), report)))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
