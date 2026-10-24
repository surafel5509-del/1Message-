package com.onemessages.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.onemessages.app.core.Logx
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.model.InboxFilter
import com.onemessages.app.model.Conversation
import com.onemessages.app.notifications.MessageNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional, low-frequency reminder for unread conversations. This is deferrable work, which
 * is exactly what WorkManager is for: it batches, respects battery constraints and survives
 * reboots. We never notify about the same message twice in a row without a new message.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: MessageNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.remindersEnabled) return Result.success()
        if (!notifier.canPostNotifications()) return Result.success()

        return runCatching {
            val unread = conversationRepository
                .observeInbox(InboxFilter.UNREAD)
                .first()
                .filter { !it.isMuted && !it.isBlocked }
                .filter { conversation ->
                    System.currentTimeMillis() - conversation.lastTimestamp >=
                        TimeUnit.MINUTES.toMillis(settings.reminderDelayMinutes.toLong())
                }
            val lastReminder = settings.lastReminderAt
            val now = System.currentTimeMillis()
            // At most one reminder notification batch per delay window.
            if (now - lastReminder < TimeUnit.MINUTES.toMillis(settings.reminderDelayMinutes.toLong())) {
                return Result.success()
            }
            unread.take(3).forEach { conversation: Conversation ->
                notifier.notifyReminder(conversation)
            }
            if (unread.isNotEmpty()) settingsRepository.setLastReminderAt(now)
            Result.success()
        }.getOrElse { error ->
            Logx.w("Reminder", "reminder work failed", error)
            Result.success() // never retry aggressively for a convenience feature
        }
    }

    companion object {
        const val WORK_NAME = "unread-reminders"
    }
}

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @com.onemessages.app.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) {
    /** Adds or removes the periodic worker to match the current preference. */
    fun syncSchedule() {
        appScope.launch {
            val settings = runCatching { settingsRepository.settings.first() }.getOrNull() ?: return@launch
            val workManager = WorkManager.getInstance(context)
            if (settings.remindersEnabled) {
                val interval = settings.reminderDelayMinutes.coerceIn(15, 24 * 60).toLong()
                val request = PeriodicWorkRequestBuilder<ReminderWorker>(interval, TimeUnit.MINUTES)
                    .setInitialDelay(interval, TimeUnit.MINUTES)
                    .build()
                runCatching {
                    workManager.enqueueUniquePeriodicWork(
                        ReminderWorker.WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request,
                    )
                }.onFailure { Logx.w("Reminder", "could not schedule reminders", it) }
            } else {
                runCatching { workManager.cancelUniqueWork(ReminderWorker.WORK_NAME) }
            }
        }
    }
}
