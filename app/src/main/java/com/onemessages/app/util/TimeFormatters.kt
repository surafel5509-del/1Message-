package com.onemessages.app.util

import android.content.Context
import android.text.format.DateUtils
import com.onemessages.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Timestamp formatting for list rows, conversation separators and message bubbles.
 * All formatting is locale aware and respects the user's 12/24-hour setting.
 */
object TimeFormatters {

    fun inboxTimestamp(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val then = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            then == today -> DateTimeFormatter.ofPattern(
                if (is24Hour(context)) "HH:mm" else "h:mm a", Locale.getDefault()
            ).format(LocalTime.ofInstant(Instant.ofEpochMilli(millis), zone))

            then == today.minusDays(1) -> context.getString(R.string.date_yesterday)
            then.year == today.year -> then.format(
                DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            )

            else -> then.format(
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
            )
        }
    }

    fun messageTime(context: Context, millis: Long): String {
        val zone = ZoneId.systemDefault()
        val pattern = if (is24Hour(context)) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
            .format(LocalTime.ofInstant(Instant.ofEpochMilli(millis), zone))
    }

    fun dateSeparator(context: Context, millis: Long, now: Long = System.currentTimeMillis()): String {
        val zone = ZoneId.systemDefault()
        val then = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            then == today -> context.getString(R.string.date_today)
            then == today.minusDays(1) -> context.getString(R.string.date_yesterday)
            then.year == today.year -> then.format(
                DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
            )

            else -> then.format(
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())
            )
        }
    }

    fun relative(millis: Long, now: Long = System.currentTimeMillis()): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            millis,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE or DateUtils.FORMAT_ABBREV_ALL
        )

    fun fullTimestamp(context: Context, millis: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(millis).atZone(zone)
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
        return dateFmt.format(date) + " · " + timeFmt.format(date)
    }

    fun dayKey(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun is24Hour(context: Context): Boolean =
        android.text.format.DateFormat.is24HourFormat(context)
}
