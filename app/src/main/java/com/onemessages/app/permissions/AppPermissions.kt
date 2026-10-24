package com.onemessages.app.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permission groups with plain-language purpose, requested only when the feature is used.
 *
 * READ_SMS is requested together with RECEIVE_SMS/SEND_SMS because conversation history is a
 * core feature; each group is still requested separately so a user can decline contacts,
 * phone state or notifications and keep messaging.
 */
enum class PermissionGroup {
    SMS,
    CONTACTS,
    NOTIFICATIONS,
    PHONE_STATE,
}

object AppPermissions {

    fun permissionsFor(group: PermissionGroup): Array<String> = when (group) {
        PermissionGroup.SMS -> arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
        PermissionGroup.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        PermissionGroup.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
        PermissionGroup.PHONE_STATE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
        } else {
            arrayOf(Manifest.permission.READ_PHONE_STATE)
        }
    }

    fun isGranted(context: Context, group: PermissionGroup): Boolean {
        val permissions = permissionsFor(group)
        if (permissions.isEmpty()) return true
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun allGranted(context: Context, group: PermissionGroup): Boolean = isGranted(context, group)

    fun hasSms(context: Context): Boolean = isGranted(context, PermissionGroup.SMS)
    fun hasContacts(context: Context): Boolean = isGranted(context, PermissionGroup.CONTACTS)
    fun hasNotifications(context: Context): Boolean = isGranted(context, PermissionGroup.NOTIFICATIONS)
    fun hasPhoneState(context: Context): Boolean = isGranted(context, PermissionGroup.PHONE_STATE)

    fun missingForOnboarding(context: Context): List<PermissionGroup> =
        PermissionGroup.entries.filterNot { isGranted(context, it) }
}
