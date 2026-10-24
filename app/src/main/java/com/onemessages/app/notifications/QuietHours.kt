package com.onemessages.app.notifications

import com.onemessages.app.data.preferences.AppSettings
import java.time.ZonedDateTime

/**
 * Pure quiet-hours maths so it can be unit tested without Android.
 *
 * Windows may cross midnight (22:00 - 07:00 is the default). Do Not Disturb is enforced by
 * the platform on top of this; we never try to bypass it.
 */
object QuietHours {

    fun isActive(settings: AppSettings, now: ZonedDateTime): Boolean {
        if (!settings.quietHoursEnabled) return false
        val minute = now.hour * 60 + now.minute
        return isWithin(settings.quietStartMinute, settings.quietEndMinute, minute)
    }

    fun isWithin(startMinute: Int, endMinute: Int, nowMinute: Int): Boolean {
        val start = startMinute.mod(24 * 60)
        val end = endMinute.mod(24 * 60)
        val now = nowMinute.mod(24 * 60)
        if (start == end) return false
        return if (start < end) {
            now in start until end
        } else {
            now >= start || now < end
        }
    }

    fun formatMinuteOfDay(minuteOfDay: Int, is24Hour: Boolean): String {
        val normalized = minuteOfDay.mod(24 * 60)
        val hour = normalized / 60
        val minute = normalized % 60
        return if (is24Hour) {
            "%02d:%02d".format(hour, minute)
        } else {
            val suffix = if (hour < 12) "AM" else "PM"
            val displayHour = when (val h = hour % 12) {
                0 -> 12
                else -> h
            }
            "%d:%02d %s".format(displayHour, minute, suffix)
        }
    }
}
