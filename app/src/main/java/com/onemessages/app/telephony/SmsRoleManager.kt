package com.onemessages.app.telephony

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.onemessages.app.core.Logx
import com.onemessages.app.model.SmsRoleState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default-SMS-role helpers.
 *
 * The role is the only supported way for a third-party app to become the system messaging
 * app. We never assume the role: it is always re-read from the platform.
 */
@Singleton
class SmsRoleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun isDefaultSmsApp(): Boolean {
        val defaultPackage = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }
            .onFailure { Logx.w("Role", "could not read default sms package", it) }
            .getOrNull()
        if (defaultPackage != null) return defaultPackage == context.packageName
        // Fall back to RoleManager when the telephony query is unavailable.
        return isRoleHeld()
    }

    fun isRoleHeld(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return isDefaultSmsAppLegacy()
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching { manager.isRoleHeld(RoleManager.ROLE_SMS) }.getOrDefault(false)
    }

    private fun isDefaultSmsAppLegacy(): Boolean =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) == context.packageName }
            .getOrDefault(false)

    fun state(): SmsRoleState {
        val roleAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_SMS) ?: true
        } else {
            true
        }
        return when {
            isDefaultSmsApp() -> SmsRoleState.DEFAULT_SMS_APP
            !roleAvailable -> SmsRoleState.ROLE_UNAVAILABLE
            else -> SmsRoleState.NOT_DEFAULT
        }
    }

    /**
     * The official role request intent (API 29+). Callers must launch it with an
     * ActivityResultLauncher; the result is authoritative, not the request itself.
     */
    fun createRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
        if (manager.isRoleHeld(RoleManager.ROLE_SMS)) return null
        return manager.createRequestRoleIntent(RoleManager.ROLE_SMS)
    }

    /** Pre-Q (minSdk 26) fallback: the documented settings action. */
    fun legacyChangeDefaultIntent(): Intent =
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
}
