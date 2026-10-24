package com.onemessages.app.notifications

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuietHoursTest {

    @Test
    fun windowInsideOneDay() {
        // 09:00 - 17:00
        assertThat(QuietHours.isWithin(9 * 60, 17 * 60, 10 * 60)).isTrue()
        assertThat(QuietHours.isWithin(9 * 60, 17 * 60, 8 * 60)).isFalse()
        assertThat(QuietHours.isWithin(9 * 60, 17 * 60, 17 * 60)).isFalse()
        // The start minute is inclusive, the end minute is not.
        assertThat(QuietHours.isWithin(9 * 60, 17 * 60, 9 * 60)).isTrue()
    }

    @Test
    fun windowCrossingMidnight() {
        // 22:00 - 07:00, the default.
        assertThat(QuietHours.isWithin(22 * 60, 7 * 60, 23 * 60)).isTrue()
        assertThat(QuietHours.isWithin(22 * 60, 7 * 60, 2 * 60)).isTrue()
        assertThat(QuietHours.isWithin(22 * 60, 7 * 60, 7 * 60)).isFalse()
        assertThat(QuietHours.isWithin(22 * 60, 7 * 60, 12 * 60)).isFalse()
    }

    @Test
    fun identicalStartAndEndMeansDisabled() {
        assertThat(QuietHours.isWithin(600, 600, 600)).isFalse()
    }

    @Test
    fun outOfRangeMinutesAreNormalised() {
        assertThat(QuietHours.isWithin(1440 + 60, 120, 90)).isTrue()
    }

    @Test
    fun formattingRespectsThe24HourSetting() {
        assertThat(QuietHours.formatMinuteOfDay(0, is24Hour = true)).isEqualTo("00:00")
        assertThat(QuietHours.formatMinuteOfDay(13 * 60 + 5, is24Hour = true)).isEqualTo("13:05")
        assertThat(QuietHours.formatMinuteOfDay(0, is24Hour = false)).isEqualTo("12:00 AM")
        assertThat(QuietHours.formatMinuteOfDay(13 * 60 + 5, is24Hour = false)).isEqualTo("1:05 PM")
        assertThat(QuietHours.formatMinuteOfDay(12 * 60, is24Hour = false)).isEqualTo("12:00 PM")
    }
}
