package com.onemessages.app.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.onemessages.app.MainActivity
import com.onemessages.app.R
import com.onemessages.app.core.Logx
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.model.Conversation
import com.onemessages.app.model.Direction
import com.onemessages.app.model.Message
import com.onemessages.app.util.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and updates message notifications.
 *
 * Design rules:
 *  - One notification per conversation, stable id, updated in place (no duplicate spam).
 *  - Reply and mark-read actions are handled by a non-exported receiver.
 *  - Previews are stripped when the user asks for privacy, and a redacted public version is
 *    attached so lock screens never reveal text.
 *  - Muted conversations and quiet hours are respected; we never bypass Do Not Disturb.
 */
@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun canPostNotifications(): Boolean {
        val enabled = manager.areNotificationsEnabled()
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return enabled && permission
    }

    suspend fun notifyIncoming(message: Message, conversation: Conversation?) {
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return
        if (!canPostNotifications()) {
            Logx.i("Notif", "notifications unavailable, skipping")
            return
        }
        if (conversation?.isMuted == true) {
            Logx.d("Notif", "conversation muted, skipping notification")
            return
        }
        if (conversation?.isBlocked == true) return

        val threadId = message.threadId
        val name = conversation?.displayName
            ?: PhoneNumbers.formatForDisplay(message.address)
        val quiet = QuietHours.isActive(settings, ZonedDateTime.now()) &&
            !(conversation?.priority == true && settings.priorityNotifications)
        val preview = settings.showMessagePreview

        val history = runCatching {
            conversationRepository.observeMessages(threadId, 8).first().messages
        }.getOrNull().orEmpty().ifEmpty { listOf(message) }

        val identity = com.onemessages.app.model.ContactIdentity(
            address = message.address,
            name = conversation?.displayName,
            photoUri = conversation?.photoUri,
            contactId = null,
        )
        val senderPerson = personFor(identity.name ?: name, conversation?.photoUri)
        val style = NotificationCompat.MessagingStyle(personFor(context.getString(R.string.you_me), null))
        val unreadCount = conversation?.unreadCount?.coerceAtLeast(1) ?: 1

        history.takeLast(8).forEach { item ->
            val text = if (preview) {
                item.previewText.takeIf { it.isNotBlank() } ?: context.getString(R.string.attachment_missing)
            } else {
                context.getString(R.string.notif_hidden_preview)
            }
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    text,
                    item.timestamp,
                    if (item.direction == Direction.INCOMING) senderPerson else null,
                )
            )
        }

        val contentIntent = openConversationIntent(threadId, message.address)
        val channelId = if (quiet) {
            NotificationChannels.SERVICE
        } else {
            NotificationChannels.channelForSound(context, conversation?.customSound ?: settings.notificationSound)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentTitle(name)
            .setContentText(
                if (preview) {
                    message.previewText.replace('\n', ' ').take(120)
                } else {
                    context.getString(R.string.notif_new_message)
                }
            )
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(
                if (settings.hideContentOnLockScreen) {
                    NotificationCompat.VISIBILITY_PRIVATE
                } else {
                    NotificationCompat.VISIBILITY_PRIVATE
                }
            )
            .setNumber(unreadCount)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(
                if (settings.groupNotifications) {
                    NotificationCompat.GROUP_ALERT_CHILDREN
                } else {
                    NotificationCompat.GROUP_ALERT_ALL
                }
            )
            .setOnlyAlertOnce(false)
            .setSilent(quiet)
            .addAction(replyAction(threadId, message.address, message.subscriptionId))
            .addAction(markReadAction(threadId))

        if (settings.vibrationEnabled && !quiet) {
            builder.setVibrate(longArrayOf(0, 120, 90, 120))
        }
        if (!preview || settings.hideContentOnLockScreen) {
            builder.setPublicVersion(publicVersion(name, threadId, message.address))
        }
        if (settings.hideContentOnLockScreen) {
            builder.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }

        runCatching {
            manager.notify(threadNotificationId(threadId), builder.build())
            if (settings.groupNotifications) postSummary(unreadCount, quiet, settings.showMessagePreview)
        }.onFailure { Logx.w("Notif", "notify failed", it) }
        Logx.d("Notif", "posted notification for thread $threadId (quiet=$quiet)")
    }

    private fun publicVersion(name: String, threadId: Long, address: String): Notification =
        NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(context.getString(R.string.notif_new_message))
            .setContentIntent(openConversationIntent(threadId, address))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private suspend fun postSummary(unread: Int, quiet: Boolean, preview: Boolean) {
        val summary = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name_brand))
            .setContentText(
                if (preview) {
                    context.resources.getQuantityString(R.plurals.notif_unread_messages, unread, unread)
                } else {
                    context.getString(R.string.notif_new_message)
                }
            )
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setSilent(quiet)
            .setContentIntent(openInboxIntent())
            .addAction(markAllReadAction())
        runCatching { manager.notify(SUMMARY_ID, summary.build()) }
    }

    private fun personFor(name: String, photoUri: String?): Person {
        val builder = Person.Builder().setName(name).setImportant(true)
        photoUri?.let { uri ->
            runCatching {
                builder.setIcon(IconCompat.createWithContentUri(Uri.parse(uri)))
            }
        }
        return builder.build()
    }

    private fun replyAction(threadId: Long, address: String, subscriptionId: Int): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(REPLY_KEY)
            .setLabel(context.getString(R.string.notif_reply_label))
            .build()
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)
            putExtra(NotificationActionReceiver.EXTRA_SUBSCRIPTION_ID, subscriptionId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, threadNotificationId(threadId))
        }
        val pending = PendingIntent.getBroadcast(
            context,
            threadNotificationId(threadId),
            intent,
            // RemoteInput requires a mutable PendingIntent so the system can attach the text.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notif_reply_label),
            pending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun markReadAction(threadId: Long): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, threadNotificationId(threadId))
        }
        val pending = PendingIntent.getBroadcast(
            context,
            threadNotificationId(threadId) + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notif_mark_read),
            pending,
        ).build()
    }

    private fun markAllReadAction(): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_ALL_READ
        }
        val pending = PendingIntent.getBroadcast(
            context,
            SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification,
            context.getString(R.string.notif_mark_all_read),
            pending,
        ).build()
    }

    private fun openConversationIntent(threadId: Long, address: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("onemessages://conversation/$threadId?address=${Uri.encode(address)}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            threadNotificationId(threadId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openInboxIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    suspend fun notifySendFailed(threadId: Long, address: String, outboxId: Long) {
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled || !canPostNotifications()) return
        if (settings.hideContentOnLockScreen || !settings.showMessagePreview) {
            // Still fine: the body below contains no message content.
        }
        val intent = openConversationIntent(threadId, address)
        val builder = NotificationCompat.Builder(context, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_send_failed_title))
            .setContentText(context.getString(R.string.notif_send_failed_body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
        runCatching {
            manager.notify(FAILED_NOTIFICATION_ID + (threadId % 1000).toInt(), builder.build())
        }.onFailure { Logx.w("Notif", "failed-send notification error", it) }
    }

    suspend fun showTestNotification(): Boolean {
        val settings = settingsRepository.settings.first()
        if (!canPostNotifications()) return false
        val builder = NotificationCompat.Builder(
            context,
            NotificationChannels.channelForSound(context, settings.notificationSound),
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_test_title))
            .setContentText(context.getString(R.string.notif_test_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_test_body))
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openInboxIntent())
        return runCatching {
            manager.notify(TEST_NOTIFICATION_ID, builder.build())
            true
        }.getOrElse {
            Logx.w("Notif", "test notification failed", it)
            false
        }
    }

    suspend fun notifyReminder(conversation: Conversation) {
        val settings = settingsRepository.settings.first()
        if (!settings.remindersEnabled || !canPostNotifications()) return
        if (!settings.notificationsEnabled || conversation.isMuted) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_reminder_title, conversation.displayName))
            .setContentText(context.getString(R.string.notif_reminder_body))
            .setContentIntent(openConversationIntent(conversation.threadId, conversation.address))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_KEY)
            .addAction(markReadAction(conversation.threadId))
        runCatching {
            // Reuses the conversation's notification id so a reminder never duplicates the
            // message notification sitting in the shade.
            manager.notify(threadNotificationId(conversation.threadId), builder.build())
        }.onFailure { Logx.w("Notif", "reminder failed", it) }
    }

    fun cancelForThread(threadId: Long) {
        runCatching { manager.cancel(threadNotificationId(threadId)) }
        runCatching { manager.cancel(SUMMARY_ID) }
    }

    fun cancelAll() {
        runCatching { manager.cancelAll() }
    }

    /**
     * Notification badge count is derived from what we post; clearing individual
     * notifications keeps the launcher badge in sync with the unread state.
     */
    fun updateBadge(count: Int) {
        if (count <= 0) {
            cancelAll()
        }
    }

    companion object {
        const val GROUP_KEY = "com.onemessages.app.MESSAGES"
        const val REPLY_KEY = "reply_text"
        const val SUMMARY_ID = 1_001
        private const val TEST_NOTIFICATION_ID = 900
        private const val FAILED_NOTIFICATION_ID = 7_000

        fun threadNotificationId(threadId: Long): Int = 2_000 + (threadId % 100_000).toInt()
    }
}
