package com.onemessages.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Number handling is the difference between replying to the right person and silently
 * starting a second thread, so these cases mirror how Ethiopian and international numbers
 * actually arrive from the network.
 */
class PhoneNumbersTest {

    @Test
    fun digitsOnly_stripsSpacesAndPunctuation() {
        assertThat(PhoneNumbers.digitsOnly("+251 91 123 4567")).isEqualTo("251911234567")
        assertThat(PhoneNumbers.digitsOnly("(091) 123-4567")).isEqualTo("0911234567")
        assertThat(PhoneNumbers.digitsOnly("")).isEmpty()
    }

    @Test
    fun looksValid_acceptsShortCodesAndFullNumbers() {
        assertThat(PhoneNumbers.looksValid("911")).isTrue()
        assertThat(PhoneNumbers.looksValid("+251911234567")).isTrue()
        assertThat(PhoneNumbers.looksValid("12")).isFalse()
        // Longer than any real MSISDN.
        assertThat(PhoneNumbers.looksValid("123456789012345678901")).isFalse()
    }

    @Test
    fun sameNumber_matchesLocalAndInternationalForms() {
        assertThat(PhoneNumbers.sameNumber("0911234567", "+251911234567")).isTrue()
        assertThat(PhoneNumbers.sameNumber("251911234567", "+251 91 123 4567")).isTrue()
        assertThat(PhoneNumbers.sameNumber("0911234567", "0911234568")).isFalse()
    }

    @Test
    fun sameNumber_blankInputIsNeverAMatch() {
        assertThat(PhoneNumbers.sameNumber("", "+251911234567")).isFalse()
        assertThat(PhoneNumbers.sameNumber("+251911234567", "")).isFalse()
    }

    @Test
    fun comparisonKey_keepsShortCodesIntact() {
        assertThat(PhoneNumbers.comparisonKey("1234")).isEqualTo("1234")
        assertThat(PhoneNumbers.comparisonKey("+251911234567")).isEqualTo("251911234567")
    }

    @Test
    fun isShortCode_recognisesFourToSixDigits() {
        assertThat(PhoneNumbers.isShortCode("8040")).isTrue()
        assertThat(PhoneNumbers.isShortCode("+251911234567")).isFalse()
    }

    @Test
    fun normalizeForSend_keepsThePlusOnlyOnce() {
        assertThat(PhoneNumbers.normalizeForSend(" +251 (91) 123-4567 ")).isEqualTo("+251911234567")
        assertThat(PhoneNumbers.normalizeForSend("0911234567")).isEqualTo("0911234567")
    }
}
