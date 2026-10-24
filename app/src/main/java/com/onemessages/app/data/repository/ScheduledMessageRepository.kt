package com.onemessages.app.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.ScheduledMessageDao
import com.onemessages.app.data.local.ScheduledMessageEntity
import com.onemessages.app.model.SendFailureReason
import com.onemessages.app.model.ScheduledMessage
import com.onemessages.app.worker.ScheduledSendWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Send later": rows in Room plus a WorkManager job per message.
 *
 * Room owns the queue (so the text survives process death, reboots and app updates) and
 * WorkManager only owns the moment (so we get doze-friendly, batched wake-ups without keeping
 * a service alive). Deleting the row and the work happens together on success.
 */
@Singleton
class ScheduledMessageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ScheduledMessageDao,
) {

    fun observeAll(): Flow<List<ScheduledMessage>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun observeForThread(threadId: Long): Flow<List<ScheduledMessage>> =
        dao.observeForThread(threadId).map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: Long): ScheduledMessage? = dao.get(id)?.toModel()

    /** Stores the message and arms the worker. Returns the new row id. */
    suspend fun schedule(
        address: String,
        body: String,
        threadId: Long,
        subscriptionId: Int,
        sendAt: Long,
    ): Long {
        val row = ScheduledMessageEntity(
            threadId = threadId,
            address = address,
            body = body,
            subscriptionId = subscriptionId,
            scheduledAt = sendAt,
        )
        val id = dao.insert(row)
        armWorker(id, sendAt)
        Logx.i("Schedule", "message scheduled")
        return id
    }

    suspend fun cancel(id: Long) {
        dao.delete(id)
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(workerName(id)) }
    }

    /** Moves an existing message to a new time and re-arms its worker. */
    suspend fun reschedule(id: Long, sendAt: Long) {
        dao.reschedule(id, sendAt)
        dao.setError(id, null)
        armWorker(id, sendAt)
    }

    suspend fun markFailed(id: Long, reason: SendFailureReason) {
        dao.setError(id, reason.name)
        dao.reschedule(id, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun snapshot(): List<ScheduledMessage> = dao.snapshot().map { it.toModel() }

    /** Restores workers after a process start in case a previous enqueue was lost. */
    suspend fun rearmPending() {
        val pending = dao.snapshot()
        if (pending.isEmpty()) return
        pending.forEach { row ->
            if (row.lastError == null) armWorker(row.id, row.scheduledAt)
        }
    }

    fun armWorker(id: Long, sendAt: Long) {
        val delayMillis = (sendAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInputData(workDataOf(ScheduledSendWorker.KEY_SCHEDULED_ID to id))
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(ScheduledSendWorker.TAG)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                workerName(id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }.onFailure { Logx.w("Schedule", "could not enqueue scheduled send", it) }
    }

    private fun workerName(id: Long): String = "scheduled-sms-$id"

    private fun ScheduledMessageEntity.toModel() = ScheduledMessage(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        subscriptionId = subscriptionId,
        scheduledAt = scheduledAt,
        createdAt = createdAt,
        lastError = lastError?.let { raw -> runCatching { SendFailureReason.valueOf(raw) }.getOrNull() },
    )
}
