package com.onemessages.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onemessages.app.core.Logx
import com.onemessages.app.data.repository.ScheduledMessageRepository
import com.onemessages.app.model.SendFailureReason
import com.onemessages.app.model.SendOutcome
import com.onemessages.app.telephony.SmsSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Sends one scheduled message when its moment arrives.
 *
 * The worker is deliberately conservative: if it wakes early it re-arms instead of sending
 * ahead of time, if the platform rejects the send it keeps the row (with the reason) so the
 * text is never lost, and it never retries on its own beyond what [SmsSender] already does.
 */
@HiltWorker
class ScheduledSendWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ScheduledMessageRepository,
    private val smsSender: SmsSender,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_SCHEDULED_ID, -1L)
        if (id <= 0L) return Result.success()
        val message = repository.get(id) ?: return Result.success()
        val now = System.currentTimeMillis()
        if (message.scheduledAt > now + EARLY_TOLERANCE_MS) {
            // WorkManager is allowed to run slightly late, never early; but OEM schedulers can
            // fire immediately after boot with a bogus clock. Re-arm rather than send early.
            repository.armWorker(id, message.scheduledAt)
            return Result.success()
        }
        val outcome = runCatching {
            smsSender.sendSms(
                address = message.address,
                body = message.body,
                threadId = message.threadId,
                subscriptionId = message.subscriptionId,
            )
        }.getOrElse { error ->
            Logx.w("Schedule", "scheduled send threw", error)
            SendOutcome.Rejected(smsSender.mapException(error))
        }
        return when (outcome) {
            is SendOutcome.Accepted -> {
                repository.delete(id)
                Logx.i("Schedule", "scheduled message accepted")
                Result.success()
            }
            is SendOutcome.Rejected -> {
                repository.markFailed(id, outcome.reason)
                Logx.w("Schedule", "scheduled message rejected: ${outcome.reason.name}", null)
                // A rejected send will not become valid by retrying blindly; the row stays for
                // the user to retry or cancel from the conversation.
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_SCHEDULED_ID = "scheduled_id"
        const val TAG = "scheduled-sms"

        /** Anything beyond this means the clock moved or the scheduler fired early. */
        private const val EARLY_TOLERANCE_MS = 5_000L
    }
}
