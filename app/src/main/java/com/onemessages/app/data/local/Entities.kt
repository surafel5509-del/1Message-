package com.onemessages.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * App-owned metadata for a system thread. We deliberately store only flags and settings —
 * message bodies and phone numbers stay in Android's SMS provider.
 */
@Entity(tableName = "conversation_meta")
data class ConversationMetaEntity(
    @PrimaryKey val threadId: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isStarred: Boolean = false,
    val isMuted: Boolean = false,
    val priority: Boolean = false,
    @ColumnInfo(name = "isBlocked") val isBlocked: Boolean = false,
    val customSound: String? = null,
    val customTitle: String? = null,
    val categoryOverride: String? = null,
    val updatedAt: Long = 0L,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val threadId: Long,
    val text: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["keyword"], unique = true)],
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val category: String,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
)

/**
 * Local technical log. Callers must pass sanitised strings; [com.onemessages.app.core.Logx]
 * scrubs digit runs and addresses before insert as a second line of defence.
 */
@Entity(tableName = "diagnostics", indices = [Index(value = ["timestamp"])])
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String,
    val tag: String,
    val message: String,
    val errorType: String? = null,
)

/**
 * Outbox rows exist so a send survives process death, can be retried safely, and is never
 * reported as delivered before the platform confirms it.
 */
@Entity(tableName = "outbox", indices = [Index(value = ["createdAt"])])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val address: String,
    val body: String,
    val subscriptionId: Int,
    val systemMessageId: Long? = null,
    val providerUri: String? = null,
    val state: String,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorCode: Int? = null,
    val isMms: Boolean = false,
    val attachmentsJson: String? = null,
)

/**
 * Starred messages. Android's SMS provider has no portable starred flag, so this is
 * app-owned metadata keyed by the provider's message id.
 */
@Entity(tableName = "starred_messages", indices = [Index(value = ["threadId"])])
data class StarredMessageEntity(
    @PrimaryKey val messageId: Long,
    val threadId: Long,
    val source: String,
    val addedAt: Long = System.currentTimeMillis(),
)
