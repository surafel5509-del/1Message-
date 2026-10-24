package com.onemessages.app.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.onemessages.app.core.Logx
import com.onemessages.app.di.ApplicationScope
import com.onemessages.app.telephony.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives SmsManager sent and delivery reports.
 *
 * The result code from the platform is the only source of truth for "sent"; this receiver
 * also persists delivery state so it survives process death.
 */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject lateinit var smsSender: SmsSender
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val outboxId = intent.getLongExtra(EXTRA_OUTBOX_ID, -1L)
        if (outboxId < 0) return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val partCount = intent.getIntExtra(EXTRA_PART_COUNT, 1)
        val uri = intent.getStringExtra(EXTRA_PROVIDER_URI)?.let(Uri::parse)
        val platformResult = resultCode
        val pending = goAsync()
        appScope.launch {
            try {
                when (action) {
                    ACTION_SMS_SENT -> smsSender.onSentResult(
                        outboxId = outboxId,
                        partIndex = partIndex,
                        partCount = partCount,
                        resultCode = platformResult,
                        uri = uri,
                    )
                    ACTION_SMS_DELIVERED -> smsSender.onDeliveredResult(outboxId, uri)
                }
            } catch (t: Throwable) {
                Logx.e("SmsStatus", "status handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.onemessages.app.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.onemessages.app.action.SMS_DELIVERED"
        const val EXTRA_OUTBOX_ID = "outbox_id"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_PART_COUNT = "part_count"
        const val EXTRA_PROVIDER_URI = "provider_uri"
        const val EXTRA_ADDRESS_KEY = "address_key"
        val OK_RESULT: Int = Activity.RESULT_OK
    }
}
