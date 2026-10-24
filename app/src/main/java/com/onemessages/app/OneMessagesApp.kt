package com.onemessages.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.onemessages.app.core.Logx
import com.onemessages.app.notifications.NotificationChannels
import com.onemessages.app.worker.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OneMessagesApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfigCompat.isDebug) android.util.Log.INFO else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        Logx.i("App", "starting 1Messages")
        NotificationChannels.ensure(this)
        // Reminders are deferrable background work, so WorkManager owns their schedule.
        reminderScheduler.syncSchedule()
    }
}

/** Single source of truth for the debug flag. */
object BuildConfigCompat {
    val isDebug: Boolean = BuildConfig.DEBUG
}
