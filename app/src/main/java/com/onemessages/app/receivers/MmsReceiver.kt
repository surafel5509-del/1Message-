package com.onemessages.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onemessages.app.R
import com.onemessages.app.core.Logx
import com.onemessages.app.notifications.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles WAP push for incoming MMS.
 *
 * Honest platform note: a third-party default SMS app can receive the push, but rendering a
 * new MMS requires the platform's internal PDU parser and provider write privileges that are
 * not part of the public SDK. Instead of pretending, we acknowledge the push, keep it out of
 * the way, and tell the user exactly what happened and what they can do. MMS that was stored
 * in the system database by another app is still displayed normally by this app.
 */
@AndroidEntryPoint
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val type = intent.type
        if (type != null && type != MMS_MIME_TYPE) return
        Logx.i("MmsReceiver", "wap push received (mime=${type ?: "unknown"})")
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                MmsNoticeFactory.notifyUnsupported(app)
            } catch (t: Throwable) {
                Logx.e("MmsReceiver", "mms notice failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val MMS_MIME_TYPE = "application/vnd.wap.mms-message"
    }
}

/** Small helper kept separate so the receiver stays readable and easy to test. */
object MmsNoticeFactory {

    private const val NOTICE_ID = 8_100

    suspend fun notifyUnsupported(context: Context) {
        NotificationChannels.ensure(context)
        val builder = NotificationCompat.Builder(context, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.mms_received_title))
            .setContentText(context.getString(R.string.mms_received_body))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(context.getString(R.string.mms_received_body))
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTICE_ID, builder.build())
        }
    }
}
