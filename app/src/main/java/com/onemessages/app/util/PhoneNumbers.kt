package com.onemessages.app.util

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Phone number helpers.
 *
 * Display formatting deliberately does not assume a single country. We use the SIM's
 * region (falling back to the device locale) so Ethiopian numbers such as
 * +251 91 123 4567 and anything else format sensibly, while comparisons use
 * E.164-normalised digits so "+251911234567", "0911234567" and "251911234567"
 * still match in search.
 */
object PhoneNumbers {

    private val SHORT_CODE_REGEX = Regex("""^\d{3,6}$""")
    private val NON_DIGIT = Regex("""[^\d+]""")

    /** Loose check used by the composer: at least 3 digits, at most 20. */
    fun looksValid(raw: String): Boolean {
        val digits = digitsOnly(raw)
        return digits.length in 3..20
    }

    fun isShortCode(raw: String): Boolean = SHORT_CODE_REGEX.matches(digitsOnly(raw))

    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Normalised form used for dedupe and search. We keep the last 9 digits as the
     * comparison key plus the full digit string, so different trunk prefixes still match.
     */
    fun comparisonKey(raw: String): String {
        val digits = digitsOnly(raw)
        if (digits.length <= 9) return digits
        // The last nine digits are the subscriber number in most numbering plans (Ethiopia
        // included), so "0911234567" and "+251911234567" collapse onto the same key.
        return digits.takeLast(9)
    }

    /** True when two numbers plausibly identify the same line. */
    fun sameNumber(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val keyA = comparisonKey(a)
        val keyB = comparisonKey(b)
        if (keyA.isEmpty() || keyB.isEmpty()) return false
        if (keyA == keyB) return true
        return keyA.endsWith(keyB) || keyB.endsWith(keyA)
    }

    fun normalizeForSend(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val cleaned = NON_DIGIT.replace(trimmed, "")
        return if (cleaned.startsWith("+")) cleaned else cleaned
    }

    /**
     * Formats for display using the region hint (SIM country or locale), never assuming
     * Ethiopia only. Falls back to grouping digits when the platform has no format.
     */
    fun formatForDisplay(raw: String, regionHint: String? = null): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        if (isShortCode(trimmed)) return trimmed
        val region = regionHint?.uppercase(Locale.ROOT)
        val formatted = runCatching {
            if (region != null && region.length == 2) {
                PhoneNumberUtils.formatNumber(trimmed, region)
            } else {
                PhoneNumberUtils.formatNumber(trimmed, Locale.getDefault().country)
            }
        }.getOrNull()
        return formatted?.takeIf { it.isNotBlank() } ?: groupDigits(trimmed)
    }

    private fun groupDigits(raw: String): String {
        val digits = digitsOnly(raw)
        if (digits.isEmpty()) return raw
        val plus = if (raw.trim().startsWith("+")) "+" else ""
        return when {
            digits.length <= 4 -> plus + digits
            digits.length <= 7 -> plus + digits.substring(0, 3) + " " + digits.substring(3)
            else -> plus + digits.substring(0, digits.length - 7).chunked(3).joinToString(" ") +
                " " + digits.substring(digits.length - 7, digits.length - 4) +
                " " + digits.substring(digits.length - 4)
        }
    }

    /** Never log or display full numbers in diagnostics; keeps the last 2 digits. */
    fun masked(raw: String): String {
        val digits = digitsOnly(raw)
        if (digits.length <= 2) return "**"
        return "***" + digits.takeLast(2)
    }

    /**
     * Splits a system thread's recipient ids ("123", "123 456", or comma separated
     * depending on ROM) and de-duplicates them.
     */
    fun parseRecipientIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(' ', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "insert-address-token" }
            .distinctBy { comparisonKey(it) }
    }
}
