package com.onemessages.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.onemessages.app.R
import com.onemessages.app.core.Logx

/**
 * Notification channels.
 *
 * Channels are immutable after creation on Android 8+, so a per-conversation sound requires
 * a dedicated channel per distinct sound URI. We keep that to one extra channel per sound
 * and never create a channel with bypassDnd - Do Not Disturb always wins.
 */
object NotificationChannels {

    const val MESSAGES = "messages_v1"
    const val REMINDERS = "reminders_v1"
    const val SERVICE = "sending_status_v1"
    private const val CUSTOM_SOUND_PREFIX = "conversation_sound_"
    private const val MAX_CUSTOM_SOUND_CHANNELS = 12

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val messages = NotificationChannel(
            MESSAGES,
            context.getString(R.string.notif_channel_messages),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_messages_desc)
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val reminders = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notif_channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_reminders_desc)
            enableVibration(false)
            setShowBadge(false)
        }

        val service = NotificationChannel(
            SERVICE,
            context.getString(R.string.notif_channel_service),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_service_desc)
            enableVibration(false)
            setShowBadge(false)
        }

        runCatching {
            manager.createNotificationChannels(listOf(messages, reminders, service))
        }.onFailure { Logx.w("Notif", "channel creation failed", it) }
    }

    /**
     * Returns a channel id that plays [soundUri], creating it when needed. Falls back to the
     * main channel when the limit is reached or no sound was chosen.
     */
    fun channelForSound(context: Context, soundUri: String?): String {
        if (soundUri.isNullOrBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return MESSAGES
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return MESSAGES
        val id = CUSTOM_SOUND_PREFIX + soundUri.hashCode().toUInt().toString(16)
        if (manager.getNotificationChannel(id) != null) return id
        val existingCustom = manager.notificationChannels.count {
            it.id.startsWith(CUSTOM_SOUND_PREFIX)
        }
        if (existingCustom >= MAX_CUSTOM_SOUND_CHANNELS) {
            Logx.w("Notif", "custom sound channel limit reached, using default channel", null)
            return MESSAGES
        }
        return runCatching {
            val channel = NotificationChannel(
                id,
                context.getString(R.string.notif_channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_messages_desc)
                enableVibration(true)
                setShowBadge(true)
                setSound(
                    Uri.parse(soundUri),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
            manager.createNotificationChannel(channel)
            id
        }.getOrDefault(MESSAGES)
    }

    /** Removes obsolete custom-sound channels when the user goes back to the default. */
    fun pruneCustomSoundChannels(context: Context, keep: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notificationChannels
            .filter { it.id.startsWith(CUSTOM_SOUND_PREFIX) && it.id != keep }
            .forEach { runCatching { manager.deleteNotificationChannel(it.id) } }
    }
}
