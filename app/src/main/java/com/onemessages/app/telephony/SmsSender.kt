package com.onemessages.app.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.OutboxDao
import com.onemessages.app.data.local.OutboxEntity
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.repository.MessageArchiveRepository
import com.onemessages.app.receivers.SmsStatusReceiver
import com.onemessages.app.model.MessageSource
import com.onemessages.app.model.OutboxState
import com.onemessages.app.model.RadioState
import com.onemessages.app.model.SendFailureReason
import com.onemessages.app.model.SendOutcome
import com.onemessages.app.model.SmsResultCodes
import com.onemessages.app.util.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends SMS through the platform SmsManager.
 *
 * Ground rules enforced here:
 *  - Nothing is reported as sent until the platform's SENT result arrives.
 *  - Rapid double taps collapse into one outbox row per (address, body) while in flight.
 *  - The message row is inserted into the system provider only when this app holds the SMS
 *    role, and its status is then updated from the same broadcasts.
 *  - Failures carry a reason the UI can explain, never a fake success.
 */
@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val outboxDao: OutboxDao,
    private val provider: SystemSmsDataSource,
    private val subscriptions: SubscriptionRepository,
    private val roleManager: SmsRoleManager,
    private val settingsRepository: SettingsRepository,
    private val archive: MessageArchiveRepository,
) {

    /** In-flight send keys; prevents duplicate sends from repeated taps across screens. */
    private val inFlight = ConcurrentHashMap<String, Long>()

    /**
     * Outbox row id -> archive row id.
     *
     * They are normally identical (the archive reuses the outbox id). A retry of an archived
     * message creates a fresh outbox row that must still update the original archive row, so
     * the mapping is explicit rather than assumed.
     */
    private val archiveLinks = ConcurrentHashMap<Long, Long>()

    /** Part completion counters per outbox row, used to decide when all parts finished. */
    private val partProgress = ConcurrentHashMap<Long, PartTracker>()

    private class PartTracker(val total: Int) {
        val succeeded = AtomicInteger(0)
        val failed = AtomicInteger(0)
    }

    suspend fun sendSms(
        address: String,
        body: String,
        threadId: Long,
        subscriptionId: Int,
        isRetry: Boolean = false,
        /** Existing archive row to update instead of creating a new one (used by retries). */
        archiveId: Long? = null,
    ): SendOutcome = withContext(Dispatchers.IO) {
        val trimmedAddress = address.trim()
        if (!PhoneNumbers.looksValid(trimmedAddress)) {
            return@withContext SendOutcome.Rejected(SendFailureReason.INVALID_RECIPIENT)
        }
        if (body.isBlank() && body.isEmpty()) {
            return@withContext SendOutcome.Rejected(SendFailureReason.UNKNOWN)
        }
        val settings = settingsRepository.settings.first()
        val sender = settings.signature.takeIf { it.isNotBlank() }
            ?.let { body + if (body.endsWith("\n")) it else "\n$it" }
            ?: body

        val radio = subscriptions.radioState()
        if (radio == RadioState.AIRPLANE_MODE) {
            return@withContext SendOutcome.Rejected(SendFailureReason.AIRPLANE_MODE)
        }
        // Enumerating SIMs needs READ_PHONE_STATE, which the user is free to deny. A missing
        // list therefore says nothing about whether the radio can send: fall back to the
        // default subscription and let SmsManager answer. Refusing here was the reason sends
        // silently failed with "no SIM" on devices where phone-state was not granted.
        var subId = subscriptions.resolveSubscription(
            preferred = settings.preferredSubscriptionId,
            threadSubscription = subscriptionId,
        )
        if (subId < 0) {
            Logx.i("Send", "no enumerable SIM, using the platform default subscription")
            subId = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
        }

        // Idempotency: identical content to the same address while a send is in flight is
        // treated as the same send instead of a duplicate.
        val key = "${PhoneNumbers.comparisonKey(trimmedAddress)}:${sender.hashCode()}"
        val existing = inFlight[key]
        if (existing != null) {
            Logx.i("Send", "duplicate tap suppressed for outbox $existing")
            return@withContext SendOutcome.Accepted(existing)
        }

        val now = System.currentTimeMillis()
        val outboxId = outboxDao.insert(
            OutboxEntity(
                threadId = threadId,
                address = trimmedAddress,
                body = sender,
                subscriptionId = subId,
                state = OutboxState.QUEUED.name,
                attempts = if (isRetry) 1 else 0,
                createdAt = now,
                updatedAt = now,
                isMms = false,
            )
        )
        inFlight[key] = outboxId

        // The archive row is written before the platform ever sees the message: if the process
        // dies mid-send (or this app is not the default SMS app), the text still exists in the
        // conversation instead of vanishing with the outbox row.
        if (archiveId != null) {
            archiveLinks[outboxId] = archiveId
            runCatching { archive.markQueued(archiveId) }
        } else {
            runCatching {
                archive.recordOutgoing(
                    address = trimmedAddress,
                    body = sender,
                    threadId = threadId,
                    subscriptionId = subId,
                    timestamp = now,
                    rowId = outboxId,
                )
            }.onFailure { Logx.w("Send", "could not archive outgoing message", it) }
        }

        // Record the outgoing message in the system database when we own the role, so other
        // apps and the system UI stay consistent.
        var providerUri: Uri? = null
        var systemMessageId: Long? = null
        if (roleManager.isDefaultSmsApp()) {
            val inserted = provider.insertOutgoingSms(
                address = trimmedAddress,
                body = sender,
                timestamp = now,
                subscriptionId = subId,
                status = Telephony.Sms.STATUS_PENDING,
            )
            if (inserted != null) {
                systemMessageId = inserted.first
                providerUri = inserted.second
                outboxDao.attachSystemMessage(outboxId, systemMessageId, providerUri?.toString())
                // A first message to a new number was archived under a synthetic thread id;
                // move it onto the real thread so the inbox and the thread agree.
                val realThread = provider.findExistingThreadId(trimmedAddress)
                if (realThread != null && realThread > 0 && realThread != threadId) {
                    archive.setThread(archiveIdFor(outboxId), realThread)
                }
                archive.attachSystemMessage(archiveIdFor(outboxId), systemMessageId, providerUri?.toString())
            }
        }

        val manager = runCatching { subscriptions.smsManagerFor(subId) }.getOrNull()
        if (manager == null) {
            finishFailure(outboxId, providerUri, systemMessageId, SendFailureReason.RADIO_ERROR)
            inFlight.remove(key)
            return@withContext SendOutcome.Rejected(SendFailureReason.RADIO_ERROR)
        }

        val parts = runCatching { manager.divideMessage(sender) }.getOrNull()
            ?: arrayListOf(sender)
        if (parts.isEmpty() || parts.any { it.isEmpty() }) {
            finishFailure(outboxId, providerUri, systemMessageId, SendFailureReason.MESSAGE_TOO_LONG)
            inFlight.remove(key)
            return@withContext SendOutcome.Rejected(SendFailureReason.MESSAGE_TOO_LONG)
        }

        partProgress[outboxId] = PartTracker(parts.size)
        val sentIntents = ArrayList<PendingIntent>(parts.size)
        val deliveryIntents = if (settings.requestDeliveryReports) {
            ArrayList<PendingIntent>(parts.size)
        } else {
            null
        }
        parts.forEachIndexed { index, _ ->
            sentIntents += buildResultIntent(
                action = SmsStatusReceiver.ACTION_SMS_SENT,
                outboxId = outboxId,
                partIndex = index,
                partCount = parts.size,
                providerUri = providerUri,
            )
            deliveryIntents?.add(
                buildResultIntent(
                    action = SmsStatusReceiver.ACTION_SMS_DELIVERED,
                    outboxId = outboxId,
                    partIndex = index,
                    partCount = parts.size,
                    providerUri = providerUri,
                )
            )
        }

        val submitted = runCatching {
            if (parts.size == 1) {
                manager.sendTextMessage(
                    trimmedAddress,
                    null,
                    parts[0],
                    sentIntents[0],
                    deliveryIntents?.get(0),
                )
            } else {
                manager.sendMultipartTextMessage(
                    trimmedAddress,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents,
                )
            }
        }

        inFlight.remove(key)

        submitted.onFailure { error ->
            Logx.e("Send", "SmsManager rejected the send", error)
            finishFailure(outboxId, providerUri, systemMessageId, mapException(error))
        }
        if (submitted.isFailure) {
            partProgress.remove(outboxId)
            return@withContext SendOutcome.Rejected(
                mapException(submitted.exceptionOrNull() ?: IllegalStateException("send failed"))
            )
        }
        Logx.i("Send", "submitted ${parts.size} part(s) via sub $subId")
        SendOutcome.Accepted(outboxId)
    }

    private fun buildResultIntent(
        action: String,
        outboxId: Long,
        partIndex: Int,
        partCount: Int,
        providerUri: Uri?,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            putExtra(SmsStatusReceiver.EXTRA_OUTBOX_ID, outboxId)
            putExtra(SmsStatusReceiver.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsStatusReceiver.EXTRA_PART_COUNT, partCount)
            putExtra(SmsStatusReceiver.EXTRA_PROVIDER_URI, providerUri?.toString())
            putExtra(SmsStatusReceiver.EXTRA_ADDRESS_KEY, "outbox:$outboxId")
        }
        val requestCode = (outboxId * 100 + partIndex + if (action.endsWith("DELIVERED")) 50_000 else 0).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun finishFailure(
        outboxId: Long,
        providerUri: Uri?,
        systemMessageId: Long?,
        reason: SendFailureReason,
    ) {
        runCatching { archive.markFailed(archiveIdFor(outboxId), reason.ordinal) }
        outboxDao.updateState(
            id = outboxId,
            state = OutboxState.FAILED.name,
            errorCode = reason.ordinal,
            attemptDelta = 1,
        )
        if (providerUri != null && roleManager.isDefaultSmsApp()) {
            provider.updateOutgoingSms(
                uri = providerUri,
                type = Telephony.Sms.MESSAGE_TYPE_FAILED,
                status = Telephony.Sms.STATUS_FAILED,
            )
        }
        Logx.w("Send", "send failed: ${reason.name}", null)
    }

    /** Called by the sent/delivery receiver with the platform result code. */
    suspend fun onSentResult(outboxId: Long, partIndex: Int, partCount: Int, resultCode: Int, uri: Uri?) {
        val tracker = partProgress.getOrPut(outboxId) { PartTracker(partCount) }
        if (resultCode == android.app.Activity.RESULT_OK) {
            val done = tracker.succeeded.incrementAndGet()
            if (done + tracker.failed.get() >= tracker.total) finalizeSuccess(outboxId, uri)
        } else {
            tracker.failed.incrementAndGet()
            finalizeFailure(outboxId, uri, resultCode)
        }
    }

    suspend fun onDeliveredResult(outboxId: Long, providerUri: Uri?) {
        runCatching { archive.markDelivered(archiveIdFor(outboxId)) }
        outboxDao.updateState(outboxId, OutboxState.DELIVERED.name, errorCode = null)
        providerUri?.let {
            if (roleManager.isDefaultSmsApp()) {
                provider.updateOutgoingSms(
                    uri = it,
                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                    status = Telephony.Sms.STATUS_PENDING,
                )
            }
        }
    }

    private suspend fun finalizeSuccess(outboxId: Long, providerUri: Uri?) {
        partProgress.remove(outboxId)
        runCatching { archive.markSent(archiveIdFor(outboxId), null, providerUri?.toString()) }
        outboxDao.updateState(outboxId, OutboxState.SENT.name, errorCode = null)
        providerUri?.let {
            if (roleManager.isDefaultSmsApp()) {
                provider.updateOutgoingSms(
                    uri = it,
                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                    status = Telephony.Sms.STATUS_COMPLETE,
                )
            }
        }
        Logx.i("Send", "platform confirmed send for outbox $outboxId")
    }

    private suspend fun finalizeFailure(outboxId: Long, providerUri: Uri?, resultCode: Int) {
        partProgress.remove(outboxId)
        runCatching { archive.markFailed(archiveIdFor(outboxId), resultCode) }
        outboxDao.updateState(
            id = outboxId,
            state = OutboxState.FAILED.name,
            errorCode = resultCode,
            attemptDelta = 1,
        )
        providerUri?.let {
            if (roleManager.isDefaultSmsApp()) {
                provider.updateOutgoingSms(
                    uri = it,
                    type = Telephony.Sms.MESSAGE_TYPE_FAILED,
                    status = Telephony.Sms.STATUS_FAILED,
                )
            }
        }
        Logx.w("Send", "platform reported failure: ${SmsResultCodes.describe(resultCode)}", null)
    }

    /** Retries a previously failed outbox row, reusing the exact same body. */
    suspend fun retry(outboxId: Long): SendOutcome = withContext(Dispatchers.IO) {
        val entry = outboxDao.get(outboxId)
            ?: return@withContext SendOutcome.Rejected(SendFailureReason.UNKNOWN)
        outboxDao.updateState(outboxId, OutboxState.QUEUED.name, errorCode = null)
        sendSms(
            address = entry.address,
            body = entry.body,
            threadId = entry.threadId,
            subscriptionId = entry.subscriptionId,
            isRetry = true,
        )
    }

    suspend fun cancel(outboxId: Long) {
        outboxDao.updateState(outboxId, OutboxState.CANCELLED.name, errorCode = null)
        runCatching { archive.delete(archiveIdFor(outboxId)) }
    }

    /**
     * Re-sends an archived message that never reached the radio.
     *
     * The archived row is reused (same id), so retrying cannot create a second copy of the
     * message in the conversation.
     */
    suspend fun retryArchived(archiveId: Long): SendOutcome = withContext(Dispatchers.IO) {
        val row = archive.get(archiveId)
            ?: return@withContext SendOutcome.Rejected(SendFailureReason.UNKNOWN)
        sendSms(
            address = row.address,
            body = row.body,
            threadId = row.threadId,
            subscriptionId = row.subscriptionId,
            isRetry = true,
            archiveId = archiveId,
        )
    }

    private fun archiveIdFor(outboxId: Long): Long = archiveLinks[outboxId] ?: outboxId

    fun mapException(error: Throwable): SendFailureReason = when (error) {
        is SecurityException -> SendFailureReason.PERMISSION_DENIED
        is IllegalArgumentException -> SendFailureReason.INVALID_RECIPIENT
        is UnsupportedOperationException -> SendFailureReason.RADIO_ERROR
        else -> SendFailureReason.UNKNOWN
    }

    /** Reconciles rows left QUEUED by process death with the provider's own status. */
    suspend fun reconcileStaleOutbox(threadId: Long) = withContext(Dispatchers.IO) {
        val pending = outboxDao.pendingForThread(threadId)
        for (entry in pending) {
            val messageId = entry.systemMessageId ?: continue
            val message = provider.loadMessage(messageId, MessageSource.SMS) ?: continue
            when (message.status) {
                com.onemessages.app.model.MessageStatus.SENT,
                com.onemessages.app.model.MessageStatus.DELIVERED ->
                    outboxDao.updateState(entry.id, OutboxState.SENT.name, errorCode = null)
                com.onemessages.app.model.MessageStatus.FAILED ->
                    outboxDao.updateState(
                        entry.id, OutboxState.FAILED.name, errorCode = entry.errorCode, attemptDelta = 1
                    )
                else -> Unit
            }
        }
    }

    companion object {
        /** Maximum parts Android will accept in one multipart SMS send. */
        const val MAX_PARTS = 10

        fun segmentCount(manager: SmsManager?, body: String): Int =
            runCatching { manager?.divideMessage(body)?.size }.getOrNull()
                ?: com.onemessages.app.util.SmsSegments.estimate(body).coerceAtLeast(1)
    }
}
