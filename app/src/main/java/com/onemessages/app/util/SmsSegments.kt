package com.onemessages.app.util

/**
 * How many SMS parts the network will charge for a body.
 *
 * A message using only the GSM 7-bit alphabet fits 160 characters in a single part and 153
 * per part once concatenated. Anything outside it (Amharic, emoji, most non-Latin scripts,
 * curly quotes) forces UCS-2: 70 characters in one part, 67 per part after that.
 *
 * The exact figure depends on the encoding the network picks, which also depends on whether
 * the user typed a GSM extension character. We classify conservatively: a body containing a
 * non-ASCII character is treated as UCS-2, which is what actually happens on the wire.
 */
object SmsSegments {

    const val GSM_SINGLE = 160
    const val GSM_MULTIPART = 153
    const val UCS2_SINGLE = 70
    const val UCS2_MULTIPART = 67

    fun isGsm7(text: String): Boolean = text.all { it.code in 0x20..0x7E || it == '\n' || it == '\r' }

    /** Zero for an empty body: nothing will be sent. */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        val gsm = isGsm7(text)
        val single = if (gsm) GSM_SINGLE else UCS2_SINGLE
        if (text.length <= single) return 1
        val multipart = if (gsm) GSM_MULTIPART else UCS2_MULTIPART
        return (text.length + multipart - 1) / multipart
    }

    /**
     * Characters left before the next part boundary, for a composer counter.
     * The first part holds 160 (or 70) characters; every part after that holds 153 (or 67).
     */
    fun remainingInPart(text: String): Int {
        val gsm = isGsm7(text)
        val single = if (gsm) GSM_SINGLE else UCS2_SINGLE
        val multipart = if (gsm) GSM_MULTIPART else UCS2_MULTIPART
        return if (text.length <= single) {
            single - text.length
        } else {
            (multipart - (text.length % multipart)) % multipart
        }
    }
}
