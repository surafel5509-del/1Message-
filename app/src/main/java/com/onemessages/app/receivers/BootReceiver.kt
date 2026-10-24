package com.onemessages.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onemessages.app.core.Logx
import com.onemessages.app.worker.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Re-arms periodic reminder work after a reboot or an app update. WorkManager persists its
 * own queue, but periodic workers are best re-synced so user settings changes apply.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        Logx.i("Boot", "rescheduling reminder work after $action")
        reminderScheduler.syncSchedule()
    }
}
