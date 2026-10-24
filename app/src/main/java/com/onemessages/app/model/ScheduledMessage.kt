package com.onemessages.app.model

/**
 * A message waiting for its scheduled send time.
 *
 * [lastError] is a [SendFailureReason] name when the platform rejected the last attempt; the UI
 * keeps the row visible in that case so the text is never lost silently.
 */
data class ScheduledMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val subscriptionId: Int,
    val scheduledAt: Long,
    val createdAt: Long,
    val lastError: SendFailureReason? = null,
) {
    val isOverdue: Boolean get() = scheduledAt <= System.currentTimeMillis()
}
