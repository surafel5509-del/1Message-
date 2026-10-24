package com.onemessages.app.model

enum class ConversationCategory {
    PERSONAL, WORK, TRANSACTIONS, VERIFICATION, UNKNOWN;

    companion object {
        fun fromStorage(value: String?): ConversationCategory? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * Everything the inbox needs in one row. Built by joining Android's thread table with our
 * own metadata (pins, archives, mutes) and a contact lookup - never by copying bodies into
 * a second database.
 */
data class Conversation(
    val threadId: Long,
    val recipientIds: List<String>,
    val displayName: String,
    val address: String,
    val snippet: String,
    val lastTimestamp: Long,
    val unreadCount: Int,
    val hasDraft: Boolean,
    val draftText: String?,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isMuted: Boolean,
    val isStarred: Boolean,
    val isGroup: Boolean,
    val isUnknownSender: Boolean,
    val lastMessageStatus: MessageStatus,
    val lastDirection: Direction,
    val lastSource: MessageSource,
    val subscriptionId: Int,
    val photoUri: String?,
    val categories: Set<ConversationCategory>,
    val hasAttachmentsInLastMessage: Boolean,
    val isBlocked: Boolean,
    val priority: Boolean,
    /** Per-conversation notification sound; null means "use the app default". */
    val customSound: String? = null,
) {
    val isUnread: Boolean get() = unreadCount > 0
}

enum class InboxFilter { ALL, UNREAD, STARRED, ARCHIVED, UNKNOWN }

enum class ConversationSort { RECENT, UNREAD_FIRST }

data class ConversationMetadata(
    val threadId: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false,
    val isMuted: Boolean = false,
    val priority: Boolean = false,
    val isBlocked: Boolean = false,
    val customSound: String? = null,
    val customTitle: String? = null,
    val categoryOverride: ConversationCategory? = null,
)

data class SearchHit(
    val threadId: Long,
    val messageId: Long,
    val displayName: String,
    val address: String,
    val snippet: String,
    val timestamp: Long,
    val matchStart: Int,
    val matchEnd: Int,
)
