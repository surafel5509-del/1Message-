package com.onemessages.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.onemessages.app.core.Logx
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.di.ApplicationScope
import com.onemessages.app.telephony.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles notification actions. Not exported, so only PendingIntents created by this app can
 * reach it - other apps cannot forge a reply or mark messages read.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var smsSender: SmsSender
    @Inject lateinit var conversationRepository: ConversationRepository
    @Inject lateinit var notifier: MessageNotifier
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        appScope.launch {
            try {
                when (action) {
                    ACTION_REPLY -> handleReply(intent)
                    ACTION_MARK_READ -> handleMarkRead(intent)
                    ACTION_MARK_ALL_READ -> handleMarkAllRead()
                }
            } catch (t: Throwable) {
                Logx.e("NotifAction", "notification action failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleReply(intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessageNotifier.REPLY_KEY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isEmpty()) return
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        val address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        val subId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1)
        if (address.isBlank()) {
            Logx.w("NotifAction", "reply had no address", null)
            return
        }
        val outcome = smsSender.sendSms(
            address = address,
            body = text,
            threadId = threadId,
            subscriptionId = subId,
        )
        Logx.i("NotifAction", "inline reply outcome=${outcome::class.simpleName}")
        // The user has engaged with this conversation from the shade, so the notification
        // has served its purpose.
        notifier.cancelForThread(threadId)
    }

    private suspend fun handleMarkRead(intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId > 0) {
            conversationRepository.markRead(threadId)
            notifier.cancelForThread(threadId)
        }
    }

    private suspend fun handleMarkAllRead() {
        val unreadThreads = conversationRepository.unreadThreadIds()
        conversationRepository.markAllRead(unreadThreads)
        notifier.cancelAll()
    }

    companion object {
        const val ACTION_REPLY = "com.onemessages.app.action.REPLY"
        const val ACTION_MARK_READ = "com.onemessages.app.action.MARK_READ"
        const val ACTION_MARK_ALL_READ = "com.onemessages.app.action.MARK_ALL_READ"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_SUBSCRIPTION_ID = "sub_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
