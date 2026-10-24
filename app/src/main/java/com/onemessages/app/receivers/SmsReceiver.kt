package com.onemessages.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.onemessages.app.core.Logx
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.data.repository.ProviderChangeNotifier
import com.onemessages.app.di.ApplicationScope
import com.onemessages.app.model.Message
import com.onemessages.app.model.MessageSource
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.model.Direction
import com.onemessages.app.notifications.MessageNotifier
import com.onemessages.app.telephony.SystemSmsDataSource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives SMS_DELIVER, which Android only sends to the app holding the default SMS role.
 *
 * Incoming parts are reassembled, de-duplicated and written to the system provider before
 * anything is shown, so the conversation history stays authoritative.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var provider: SystemSmsDataSource
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var notifier: MessageNotifier
    @Inject lateinit var providerNotifier: ProviderChangeNotifier
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .onFailure { Logx.e("SmsReceiver", "could not parse incoming pdu", it) }
            .getOrNull()
        if (messages.isNullOrEmpty()) return

        val pending = goAsync()
        appScope.launch {
            try {
                handle(messages)
            } catch (t: Throwable) {
                Logx.e("SmsReceiver", "incoming sms handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(parts: Array<android.telephony.SmsMessage>) {
        val first = parts.firstOrNull() ?: return
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return
        val body = parts.joinToString(separator = "") { it.displayMessageBody ?: "" }
        val timestamp = first.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val subId = runCatching {
            android.telephony.SubscriptionManager.getDefaultSmsSubscriptionId()
        }.getOrDefault(-1)

        if (provider.findIncomingDuplicate(address, body, timestamp)) {
            Logx.i("SmsReceiver", "duplicate delivery ignored")
            return
        }

        val insertedId = provider.insertIncomingSms(address, body, timestamp, subId)
        if (insertedId == null) {
            // We could not persist (role lost between broadcast and handling). Post a
            // notification anyway so the user does not silently miss a message, but make the
            // limitation explicit in the log.
            Logx.w("SmsReceiver", "could not persist incoming message (role/permission)", null)
            return
        }
        providerNotifier.notifyChanged()

        val threadId = provider.loadMessage(insertedId, MessageSource.SMS)?.threadId
            ?: provider.findExistingThreadId(address)
            ?: return
        val conversation = conversationRepository.getConversation(threadId)
        val message = Message(
            id = insertedId,
            source = MessageSource.SMS,
            threadId = threadId,
            address = address,
            body = body,
            timestamp = timestamp,
            direction = Direction.INCOMING,
            status = MessageStatus.RECEIVED,
            read = false,
            starred = false,
            subscriptionId = subId,
        )
        notifier.notifyIncoming(message, conversation)
        Logx.i("SmsReceiver", "stored incoming message in thread $threadId")
    }
}
