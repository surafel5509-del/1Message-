package com.onemessages.app.domain

/**
 * Local, rule-based reply suggestions.
 *
 * Deliberately not an on-device language model: suggestions are three fixed, localisable
 * phrases picked by simple intent cues, which keeps them instant, predictable, testable and
 * entirely offline. Nothing about the message leaves the device, and the user can turn the
 * whole feature off.
 */
enum class SmartReplyKind {
    YES,
    NO,
    THANKS,
    OK,
    ON_MY_WAY,
    CALL_ME,
    GREETING,
    SEE_YOU_SOON,
}

object SmartReplies {

    private val GREETING_WORDS = listOf("hi", "hello", "hey", "selam", "ሰላም", "ሄይ", "ሰላምታ")
    private val CALL_WORDS = listOf("call", "phone me", "dial", "ደውል", "ይደውሉ")
    private val TIME_WORDS = listOf("when", "time", "what time", "tonight", "tomorrow", "መቼ", "ሰዓት")
    private val THANKS_WORDS = listOf("thank", "thanks", "አመሰግናለሁ", "እናመሰግናለን")
    private val YES_NO_CUES = listOf("?", "can you", "are you", "will you", "ok?", "ነው?", "ትችላለህ")

    /**
     * @param lastIncomingBody the most recent message received in the conversation, or null
     *   when the last message is outgoing (in which case we suggest nothing).
     */
    fun suggest(lastIncomingBody: String?, limit: Int = 3): List<SmartReplyKind> {
        val body = lastIncomingBody?.trim().orEmpty()
        if (body.isEmpty() || limit <= 0) return emptyList()
        val lower = body.lowercase()
        val picked = LinkedHashSet<SmartReplyKind>()

        if (containsAny(lower, GREETING_WORDS) && lower.length <= 40) {
            picked += SmartReplyKind.GREETING
            picked += SmartReplyKind.YES
        } else if (containsAny(lower, CALL_WORDS)) {
            picked += SmartReplyKind.CALL_ME
            picked += SmartReplyKind.YES
        } else if (endsWithQuestion(lower)) {
            // Questions win over topic words: "are you coming tonight?" wants a yes/no even
            // though it mentions a time.
            picked += SmartReplyKind.YES
            picked += SmartReplyKind.NO
        } else if (containsAny(lower, TIME_WORDS)) {
            picked += SmartReplyKind.ON_MY_WAY
            picked += SmartReplyKind.SEE_YOU_SOON
        } else if (containsAny(lower, THANKS_WORDS)) {
            picked += SmartReplyKind.OK
        }

        // Always offer a neutral acknowledgement so there is a sensible chip in every case.
        picked += SmartReplyKind.OK
        picked += SmartReplyKind.THANKS
        picked += SmartReplyKind.ON_MY_WAY
        return picked.take(limit)
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { needle -> haystack.contains(needle) }

    private fun endsWithQuestion(lower: String): Boolean =
        lower.contains("?") || YES_NO_CUES.any { lower.contains(it) }
}
