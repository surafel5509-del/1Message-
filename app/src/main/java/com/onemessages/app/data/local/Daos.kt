package com.onemessages.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationMetaDao {

    @Query("SELECT * FROM conversation_meta")
    fun observeAll(): Flow<List<ConversationMetaEntity>>

    @Query("SELECT * FROM conversation_meta WHERE threadId = :threadId LIMIT 1")
    suspend fun get(threadId: Long): ConversationMetaEntity?

    @Query("SELECT * FROM conversation_meta WHERE threadId = :threadId LIMIT 1")
    fun observe(threadId: Long): Flow<ConversationMetaEntity?>

    @Query("SELECT threadId FROM conversation_meta WHERE isArchived = 1")
    suspend fun archivedThreadIds(): List<Long>

    @Upsert
    suspend fun upsert(entity: ConversationMetaEntity)

    @Query("DELETE FROM conversation_meta WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)

    @Query("DELETE FROM conversation_meta")
    suspend fun clear()

    @Query("SELECT * FROM conversation_meta")
    suspend fun snapshot(): List<ConversationMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ConversationMetaEntity>)
}

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts")
    fun observeAll(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE threadId = :threadId LIMIT 1")
    fun observe(threadId: Long): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts WHERE threadId = :threadId LIMIT 1")
    suspend fun get(threadId: Long): DraftEntity?

    @Upsert
    suspend fun upsert(entity: DraftEntity)

    @Query("DELETE FROM drafts WHERE threadId = :threadId")
    suspend fun delete(threadId: Long)

    @Query("DELETE FROM drafts")
    suspend fun clear()

    @Query("SELECT * FROM drafts")
    suspend fun snapshot(): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DraftEntity>)
}

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules ORDER BY isBuiltIn DESC, id ASC")
    fun observeAll(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<CategoryRuleEntity>

    @Upsert
    suspend fun upsert(entity: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteUserRule(id: Long)

    @Query("SELECT COUNT(*) FROM category_rules")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CategoryRuleEntity>)

    @Query("DELETE FROM category_rules WHERE isBuiltIn = 0")
    suspend fun clearUserRules()
}

@Dao
interface DiagnosticsDao {

    @Query("SELECT * FROM diagnostics ORDER BY timestamp DESC LIMIT 300")
    fun observeRecent(): Flow<List<DiagnosticEntity>>

    @Insert
    suspend fun insert(entity: DiagnosticEntity)

    @Query("SELECT * FROM diagnostics ORDER BY timestamp DESC LIMIT 300")
    suspend fun recent(): List<DiagnosticEntity>

    @Query("DELETE FROM diagnostics")
    suspend fun clear()

    @Query("DELETE FROM diagnostics WHERE timestamp < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM diagnostics")
    suspend fun count(): Int
}

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox WHERE state IN ('QUEUED', 'FAILED') ORDER BY createdAt ASC")
    fun observePending(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): OutboxEntity?

    @Insert
    suspend fun insert(entity: OutboxEntity): Long

    @Query(
        """
        UPDATE outbox SET state = :state, updatedAt = :timestamp, errorCode = :errorCode,
        attempts = attempts + :attemptDelta
        WHERE id = :id
        """
    )
    suspend fun updateState(
        id: Long,
        state: String,
        errorCode: Int?,
        timestamp: Long = System.currentTimeMillis(),
        attemptDelta: Int = 0,
    )

    @Query("UPDATE outbox SET systemMessageId = :messageId, providerUri = :uri, state = 'SENT' WHERE id = :id")
    suspend fun attachSystemMessage(id: Long, messageId: Long?, uri: String?)

    @Query("DELETE FROM outbox WHERE state IN ('SENT', 'DELIVERED', 'CANCELLED') AND updatedAt < :before")
    suspend fun pruneCompleted(before: Long)

    @Query("SELECT * FROM outbox WHERE threadId = :threadId AND state IN ('QUEUED','FAILED')")
    suspend fun pendingForThread(threadId: Long): List<OutboxEntity>

    @Query("DELETE FROM outbox")
    suspend fun clear()
}

@Dao
interface StarredMessageDao {

    @Query("SELECT * FROM starred_messages ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<StarredMessageEntity>>

    @Query("SELECT * FROM starred_messages ORDER BY addedAt DESC")
    suspend fun snapshot(): List<StarredMessageEntity>

    @Query("SELECT messageId FROM starred_messages")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT * FROM starred_messages WHERE threadId = :threadId")
    suspend fun forThread(threadId: Long): List<StarredMessageEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM starred_messages WHERE messageId = :messageId)")
    suspend fun isStarred(messageId: Long): Boolean

    @Upsert
    suspend fun upsert(entity: StarredMessageEntity)

    @Query("DELETE FROM starred_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)

    @Query("DELETE FROM starred_messages WHERE threadId = :threadId")
    suspend fun deleteForThread(threadId: Long)

    @Query("DELETE FROM starred_messages")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<StarredMessageEntity>)
}

@Dao
interface ScheduledMessageDao {

    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE threadId = :threadId ORDER BY scheduledAt ASC")
    fun observeForThread(threadId: Long): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ScheduledMessageEntity?

    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledAt ASC")
    suspend fun snapshot(): List<ScheduledMessageEntity>

    @Insert
    suspend fun insert(entity: ScheduledMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ScheduledMessageEntity>)

    @Query("UPDATE scheduled_messages SET lastError = :error WHERE id = :id")
    suspend fun setError(id: Long, error: String?)

    @Query("UPDATE scheduled_messages SET scheduledAt = :sendAt WHERE id = :id")
    suspend fun reschedule(id: Long, sendAt: Long)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scheduled_messages")
    suspend fun clear()
}
