package com.onemessages.app.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.onemessages.app.core.Logx
import com.onemessages.app.model.DeviceTelephonyState
import com.onemessages.app.model.RadioState
import com.onemessages.app.model.SimInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SIM enumeration, radio state and per-subscription SmsManager access.
 *
 * All failures degrade to "unknown" rather than crashing: telephony behaviour varies a lot
 * between OEMs, dual-SIM implementations and emulators.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roleManager: SmsRoleManager,
) {

    fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasPhoneNumbersPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS) ==
            PackageManager.PERMISSION_GRANTED

    private val subscriptionManager: SubscriptionManager?
        get() = context.getSystemService(SubscriptionManager::class.java)

    fun sims(): List<SimInfo> {
        if (!hasPhoneStatePermission()) return defaultOnlySims()
        val manager = subscriptionManager ?: return defaultOnlySims()
        val infos = runCatching { manager.activeSubscriptionInfoList }
            .onFailure { Logx.w("Sim", "subscription list unavailable", it) }
            .getOrNull()
        if (infos.isNullOrEmpty()) return defaultOnlySims()
        val defaultSms = defaultSmsSubscriptionId()
        val defaultData = runCatching { SubscriptionManager.getDefaultDataSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        return infos.mapNotNull { info ->
            val subId = info.subscriptionId
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return@mapNotNull null
            SimInfo(
                subscriptionId = subId,
                slotIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.simSlotIndex else -1,
                carrierName = info.carrierName?.toString(),
                displayName = runCatching { info.displayName?.toString() }.getOrNull(),
                number = if (hasPhoneNumbersPermission()) {
                    runCatching { info.number }.getOrNull()?.takeIf { it.isNotBlank() }
                } else {
                    null
                },
                isDefaultForSms = subId == defaultSms,
                isDefaultForData = subId == defaultData,
            )
        }.sortedBy { it.slotIndex }
    }

    private fun defaultOnlySims(): List<SimInfo> {
        val subId = defaultSmsSubscriptionId()
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return emptyList()
        val carrier = runCatching {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)
                ?.networkOperatorName
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return listOf(
            SimInfo(
                subscriptionId = subId,
                slotIndex = -1,
                carrierName = carrier,
                displayName = null,
                number = null,
                isDefaultForSms = true,
                isDefaultForData = subId == runCatching {
                    SubscriptionManager.getDefaultDataSubscriptionId()
                }.getOrDefault(-1),
            )
        )
    }

    fun defaultSmsSubscriptionId(): Int =
        runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    fun airplaneModeEnabled(): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }.getOrDefault(false)

    fun radioState(): RadioState {
        if (airplaneModeEnabled()) return RadioState.AIRPLANE_MODE
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return RadioState.UNKNOWN
        val simState = runCatching { telephony.simState }.getOrDefault(TelephonyManager.SIM_STATE_UNKNOWN)
        if (simState == TelephonyManager.SIM_STATE_ABSENT) return RadioState.NO_SIM
        val service = runCatching { telephony.serviceState }.getOrNull()
        return when {
            service == null -> RadioState.UNKNOWN
            service.state == android.telephony.ServiceState.STATE_IN_SERVICE -> RadioState.READY
            service.state == android.telephony.ServiceState.STATE_EMERGENCY_ONLY -> RadioState.NO_SERVICE
            service.state == android.telephony.ServiceState.STATE_OUT_OF_SERVICE -> RadioState.NO_SERVICE
            service.state == android.telephony.ServiceState.STATE_POWER_OFF -> RadioState.NO_SERVICE
            else -> RadioState.UNKNOWN
        }
    }

    /** ISO region for number formatting; SIM country first, then network, then locale. */
    fun regionHint(): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sim = runCatching { telephony?.simCountryIso }.getOrNull()
        if (!sim.isNullOrBlank()) return sim.uppercase(java.util.Locale.ROOT)
        val network = runCatching { telephony?.networkCountryIso }.getOrNull()
        if (!network.isNullOrBlank()) return network.uppercase(java.util.Locale.ROOT)
        return java.util.Locale.getDefault().country.takeIf { it.length == 2 }
    }

    fun isMobileDataEnabled(): Boolean = runCatching {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        telephony?.isDataEnabled ?: false
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    fun smsManagerFor(subscriptionId: Int): SmsManager = if (subscriptionId >= 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
    }

    fun deviceState(): DeviceTelephonyState {
        val sims = sims()
        val radio = radioState()
        val role = roleManager.state()
        val canSend = sims.isNotEmpty() &&
            radio != RadioState.AIRPLANE_MODE &&
            radio != RadioState.NO_SIM
        return DeviceTelephonyState(
            radioState = radio,
            sims = sims,
            smsRoleState = role,
            canSendSms = canSend,
            canSendMms = canSend && isMobileDataEnabled(),
        )
    }

    /** Chooses the SIM for a send, honouring the user's preference and the thread's SIM. */
    fun resolveSubscription(preferred: Int, threadSubscription: Int): Int {
        val available = sims().map { it.subscriptionId }
        return when {
            threadSubscription >= 0 && threadSubscription in available -> threadSubscription
            preferred >= 0 && preferred in available -> preferred
            available.isNotEmpty() -> available.first()
            else -> SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
    }
}
