package com.onemessages.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.onemessages.app.core.Logx
import com.onemessages.app.di.ApplicationScope
import com.onemessages.app.telephony.SmsSender
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles "Respond via message" from the dialer during an incoming call. This service is one
 * of the components Android requires for the default SMS role.
 */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {

    @Inject lateinit var smsSender: SmsSender
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val recipients = intent.data?.schemeSpecificPart
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subId = intent.getIntExtra("subscription", -1)
        if (recipients.isEmpty() || text.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        appScope.launch {
            recipients.forEach { recipient ->
                runCatching {
                    smsSender.sendSms(
                        address = recipient,
                        body = text,
                        threadId = -1,
                        subscriptionId = subId,
                    )
                }.onFailure { Logx.e("HeadlessSms", "quick reply failed", it) }
            }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}
