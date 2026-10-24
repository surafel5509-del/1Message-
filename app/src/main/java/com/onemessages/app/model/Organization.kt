package com.onemessages.app.model

/** A user-editable classification rule. Matching is case-insensitive substring matching. */
data class CategoryRule(
    val id: Long = 0,
    val keyword: String,
    val category: ConversationCategory,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
)

/** A verification code found locally in a message body. Never leaves the device. */
data class OtpMatch(
    val code: String,
    val messageId: Long,
    val threadId: Long,
    val detectedAt: Long,
)
