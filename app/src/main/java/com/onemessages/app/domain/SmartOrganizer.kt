package com.onemessages.app.domain

import com.onemessages.app.model.CategoryRule
import com.onemessages.app.model.ConversationCategory
import com.onemessages.app.model.OtpMatch
import com.onemessages.app.util.PhoneNumbers

/**
 * Local, explainable organisation of messages.
 *
 * Nothing here is clever about your bank: a message that *mentions* money is only marked
 * with a category and a warning. We never validate senders, open links or enter codes.
 */
object SmartOrganizer {

    /** Rules shipped with the app. All are editable/removable and only run on-device. */
    val builtInRules: List<CategoryRule> = listOf(
        CategoryRule(keyword = "otp", category = ConversationCategory.VERIFICATION, isBuiltIn = true),
        CategoryRule(keyword = "verification code", category = ConversationCategory.VERIFICATION, isBuiltIn = true),
        CategoryRule(keyword = "one-time", category = ConversationCategory.VERIFICATION, isBuiltIn = true),
        CategoryRule(keyword = "passcode", category = ConversationCategory.VERIFICATION, isBuiltIn = true),
        CategoryRule(keyword = "do not share", category = ConversationCategory.VERIFICATION, isBuiltIn = true),
        CategoryRule(keyword = "debited", category = ConversationCategory.TRANSACTIONS, isBuiltIn = true),
        CategoryRule(keyword = "credited", category = ConversationCategory.TRANSACTIONS, isBuiltIn = true),
        CategoryRule(keyword = "transaction", category = ConversationCategory.TRANSACTIONS, isBuiltIn = true),
        CategoryRule(keyword = "balance", category = ConversationCategory.TRANSACTIONS, isBuiltIn = true),
        CategoryRule(keyword = "invoice", category = ConversationCategory.WORK, isBuiltIn = true),
        CategoryRule(keyword = "meeting", category = ConversationCategory.WORK, isBuiltIn = true),
        CategoryRule(keyword = "deadline", category = ConversationCategory.WORK, isBuiltIn = true),
    )

    private val OTP_KEYWORDS = listOf(
        "otp", "code", "verification", "verify", "passcode", "pin", "password", "login",
        "confirmation", "authenticate",
    )

    /** Separate words often used in Ethiopian bank/telecom alerts. Localisation-friendly. */
    private val FINANCIAL_KEYWORDS = listOf(
        "debited", "credited", "balance", "transaction", "transfer", "payment", "receipt",
        "birr", "etb", "invoice", "airtime", "top-up", "topup",
    )

    private val CODE_REGEX = Regex("""(?<!\d)(\d{4,8})(?!\d)""")
    private val ALPHANUMERIC_CODE_REGEX = Regex("""(?<![A-Za-z0-9])([A-Z0-9]{6,8})(?![A-Za-z0-9])""")
    private val YEAR_REGEX = Regex("""^(19|20)\d{2}$""")

    /** A message this short with a single number is almost certainly a forwarded code. */
    private const val SHORT_CODE_MESSAGE_LENGTH = 60

    data class Classification(
        val categories: Set<ConversationCategory>,
        val matchedKeyword: String?,
        val hasFinancialWording: Boolean,
    )

    /**
     * Classifies a message using keyword rules. A conversation with an unknown address also
     * gets [ConversationCategory.UNKNOWN] so the Unknown filter can surface it; that is a
     * fact about the address book, not a judgement about the sender.
     */
    fun classify(
        body: String?,
        address: String,
        rules: List<CategoryRule>,
        isKnownContact: Boolean,
    ): Classification {
        val text = body.orEmpty()
        val haystack = text.lowercase()
        val addressLower = address.lowercase()
        val matched = LinkedHashSet<ConversationCategory>()
        var matchedKeyword: String? = null

        for (rule in rules) {
            if (!rule.enabled || rule.keyword.isBlank()) continue
            val keyword = rule.keyword.trim().lowercase()
            if (haystack.contains(keyword) || addressLower.contains(keyword)) {
                matched += rule.category
                if (matchedKeyword == null) matchedKeyword = rule.keyword
            }
        }
        if (!isKnownContact && address.isNotBlank()) matched += ConversationCategory.UNKNOWN
        val financial = FINANCIAL_KEYWORDS.any { haystack.contains(it) }
        return Classification(matched, matchedKeyword, financial)
    }

    /**
     * Finds a plausible one-time code.
     *
     * Constraints that keep false positives low:
     *  - 4 to 8 digits, not part of a longer number (phone numbers are 9+ digits here).
     *  - must sit within 48 characters of a verification keyword, or the message must be
     *    short and contain no other long number.
     *  - four-digit years are rejected.
     *  - alphanumeric codes only when a keyword is adjacent.
     */
    fun findOtp(body: String, messageId: Long, threadId: Long, now: Long = System.currentTimeMillis()): OtpMatch? {
        val text = body.trim()
        if (text.isEmpty() || text.length > 600) return null
        val lower = text.lowercase()
        val keywordIndex = firstKeywordIndex(lower) ?: -1

        val numericCandidates = CODE_REGEX.findAll(text)
            .map { it.value }
            .filterNot { YEAR_REGEX.matches(it) }
            .toList()
        // A code only counts when it is anchored by a verification word, or when the whole
        // message is essentially just the number (people forward codes to themselves). Without
        // this, an ordinary "meet me at 123456" line would be masked as a one-time code.
        val numeric = if (keywordIndex >= 0) {
            numericCandidates.firstOrNull { isNear(text, it, keywordIndex) }
        } else {
            val onlyNumber = text.length <= SHORT_CODE_MESSAGE_LENGTH &&
                numericCandidates.size == 1 &&
                text.count { it.isDigit() } == numericCandidates.first().length
            numericCandidates.firstOrNull().takeIf { onlyNumber }
        }

        if (numeric != null) {
            return OtpMatch(code = numeric, messageId = messageId, threadId = threadId, detectedAt = now)
        }
        if (keywordIndex >= 0) {
            val alphanumeric = ALPHANUMERIC_CODE_REGEX.findAll(text)
                .map { it.value }
                .filter { it.any { ch -> ch.isLetter() } && it.any { ch -> ch.isDigit() } }
                .firstOrNull { candidate -> isNear(text, candidate, keywordIndex) }
            if (alphanumeric != null) {
                return OtpMatch(code = alphanumeric, messageId = messageId, threadId = threadId, detectedAt = now)
            }
        }
        return null
    }

    private fun firstKeywordIndex(lower: String): Int? {
        val indices = OTP_KEYWORDS.mapNotNull { keyword ->
            lower.indexOf(keyword).takeIf { it >= 0 }
        }
        return indices.minOrNull()
    }

    private fun isNear(text: String, candidate: String, keywordIndex: Int): Boolean {
        val candidateIndex = text.indexOf(candidate)
        if (candidateIndex < 0) return false
        val distance = if (candidateIndex > keywordIndex) {
            candidateIndex - keywordIndex
        } else {
            keywordIndex - (candidateIndex + candidate.length)
        }
        return distance <= 48
    }

    /** Mask helper for the "hide codes" setting: keeps length visible, digits hidden. */
    fun maskCode(code: String): String = code.map { if (it.isDigit() || it.isLetter()) '•' else it }.joinToString("")

    fun categoriesForAddressAppearsUnknown(address: String): Boolean = address.isNotBlank()

    /** True when a body looks like a request to act urgently; used only for UI hints. */
    fun looksLikeUrgentScam(body: String): Boolean {
        val lower = body.lowercase()
        val urgency = listOf("urgent", "immediately", "within 24", "suspend", "blocked", "verify now")
        val linkAsk = listOf("click", "http://", "https://", "call this number", "send your pin")
        return urgency.any { lower.contains(it) } && linkAsk.any { lower.contains(it) }
    }

    fun normalizedSenderKey(address: String): String = PhoneNumbers.comparisonKey(address)
}
