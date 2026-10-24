package com.onemessages.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsSegmentsTest {

    @Test
    fun emptyBodyCostsNothing() {
        assertThat(SmsSegments.estimate("")).isEqualTo(0)
    }

    @Test
    fun gsmTextFitsOnePartUpTo160() {
        assertThat(SmsSegments.estimate("a".repeat(160))).isEqualTo(1)
        assertThat(SmsSegments.estimate("a".repeat(161))).isEqualTo(2)
        // 153 characters per part once concatenated: 306 characters is two parts.
        assertThat(SmsSegments.estimate("a".repeat(306))).isEqualTo(2)
        assertThat(SmsSegments.estimate("a".repeat(307))).isEqualTo(3)
    }

    @Test
    fun amharicAndEmojiFallBackToUcs2() {
        assertThat(SmsSegments.isGsm7("hello")).isTrue()
        assertThat(SmsSegments.isGsm7("ሰላም")).isFalse()
        assertThat(SmsSegments.estimate("ሰላም")).isEqualTo(1)
        assertThat(SmsSegments.estimate("ሰ".repeat(70))).isEqualTo(1)
        assertThat(SmsSegments.estimate("ሰ".repeat(71))).isEqualTo(2)
        assertThat(SmsSegments.estimate("a".repeat(100) + "ሰ")).isEqualTo(2)
    }

    @Test
    fun remainingInPartCountsDownAndWraps() {
        assertThat(SmsSegments.remainingInPart("")).isEqualTo(160)
        assertThat(SmsSegments.remainingInPart("hello")).isEqualTo(155)
        assertThat(SmsSegments.remainingInPart("a".repeat(160))).isEqualTo(0)
        assertThat(SmsSegments.remainingInPart("a".repeat(153))).isEqualTo(0)
        assertThat(SmsSegments.remainingInPart("a".repeat(154))).isEqualTo(152)
        assertThat(SmsSegments.remainingInPart("ሰ".repeat(70))).isEqualTo(0)
    }
}
