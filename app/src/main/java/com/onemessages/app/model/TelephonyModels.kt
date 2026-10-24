package com.onemessages.app.model

import android.telephony.SmsManager

/** A physical SIM as Android reports it. [slotIndex] may be -1 on some devices. */
data class SimInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String?,
    val displayName: String?,
    val number: String?,
    val isDefaultForSms: Boolean,
    val isDefaultForData: Boolean,
) {
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: carrierName?.takeIf { it.isNotBlank() }
            ?: number
            ?: "SIM ${slotIndex + 1}"
}

enum class SmsRoleState {
    /** App holds RoleManager.ROLE_SMS (or is the system default SMS app). */
    DEFAULT_SMS_APP,

    /** App does not hold the role but can still read/send with runtime permissions. */
    NOT_DEFAULT,

    /** Platform reports no app can hold this role (rare, e.g. restricted profiles). */
    ROLE_UNAVAILABLE,
}

enum class RadioState { UNKNOWN, READY, NO_SIM, AIRPLANE_MODE, NO_SERVICE }

data class DeviceTelephonyState(
    val radioState: RadioState,
    val sims: List<SimInfo>,
    val smsRoleState: SmsRoleState,
    val canSendSms: Boolean,
    val canSendMms: Boolean,
) {
    val hasSim: Boolean get() = sims.isNotEmpty()
}

/** Input for a single outgoing SMS. Parts are computed by Android's multipart API. */
data class OutgoingSms(
    val address: String,
    val body: String,
    val subscriptionId: Int,
    val threadId: Long,
    val requestDeliveryReport: Boolean,
    val isRetry: Boolean = false,
)

/** A row in our local outbox, used to survive process death and to power retries. */
data class OutboxEntry(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val subscriptionId: Int,
    val systemMessageId: Long?,
    val providerUri: String?,
    val state: OutboxState,
    val attempts: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val errorCode: Int?,
    val isMms: Boolean,
    val attachmentsJson: String?,
)

enum class OutboxState { QUEUED, SENT, DELIVERED, FAILED, CANCELLED }

/** Result codes we surface in diagnostics; mapped to friendly strings in the UI. */
object SmsResultCodes {
    fun describe(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic_failure"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "no_service"
        SmsManager.RESULT_ERROR_NULL_PDU -> "null_pdu"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "radio_off"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "short_code_not_allowed"
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "short_code_never_allowed"
        else -> "code_$resultCode"
    }
}
