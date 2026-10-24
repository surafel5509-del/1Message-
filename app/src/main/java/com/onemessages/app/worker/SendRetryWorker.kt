package com.onemessages.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.onemessages.app.core.Logx
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.telephony.SmsSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * One-shot retry of a failed SMS, scheduled only when the user has auto-retry enabled and
 * the platform reported a transient failure. Bounded to a single attempt.
 */
@HiltWorker
class SendRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val smsSender: SmsSender,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outboxId = inputData.getLong(KEY_OUTBOX_ID, -1L)
        if (outboxId <= 0) return Result.success()
        val settings = settingsRepository.settings.first()
        if (!settings.autoRetryFailedSends) return Result.success()
        return runCatching {
            smsSender.retry(outboxId)
            Result.success()
        }.getOrElse {
            Logx.w("Retry", "retry worker failed", it)
            Result.success()
        }
    }

    companion object {
        private const val KEY_OUTBOX_ID = "outbox_id"

        fun schedule(context: Context, outboxId: Long) {
            val request = OneTimeWorkRequestBuilder<SendRetryWorker>()
                .setInputData(workDataOf(KEY_OUTBOX_ID to outboxId))
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueue(request)
            }
        }
    }
}
