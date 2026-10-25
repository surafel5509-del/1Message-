package com.onemessages.app.data.repository

import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.LocalMessageDao
import com.onemessages.app.data.local.LocalMessageEntity
import com.onemessages.app.model.Direction
import com.onemessages.app.model.Message
import com.onemessages.app.model.MessageSource
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * The app's own message archive.
 *
 * Android's provider is writable only while this app is the default SMS app, so on its own it
 * cannot answer the two questions that matter most: *did my message really go out?* and *what
 * did I send before I had the role?* This repository records every outgoing message **before**
 * it reaches SmsManager and every incoming broadcast **before** the provider is touched, then
 * links the row to the provider copy when one exists.
 *
 * Message ids are offset into a negative range so an archived row can never be confused with a
 * provider row id or with an outbox id when the UI keys lists and looks up retries.
 */
@Singleton
class MessageArchiveRepository @Inject constructor(
    private val dao: LocalMessageDao,
    private val notifier: ProviderChangeNotifier,
) {

    /** Room writes are invisible to the provider observer, so every write nudges the UI. */
    private fun changed() = notifier.notifyChanged()

    /** Snapshot of everything archived for a thread (or an address, for pre-thread rows). */
    suspend fun forConversation(threadId: Long, address: String): List<Message> =
        withContext(Dispatchers.IO) {
            val rows = if (threadId > 0) {
                dao.forThread(threadId).ifEmpty { dao.forAddress(addressKey(address)) }
            } else {
                dao.forAddress(addressKey(address))
            }
            rows.map { toMessage(it) }
        }

    suspend fun forThread(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        rowsForThread(threadId).map { toMessage(it) }
    }

    /** Raw rows for a thread; callers need [LocalMessageEntity.systemMessageId] for de-duplication. */
    suspend fun rowsForThread(threadId: Long): List<LocalMessageEntity> = withContext(Dispatchers.IO) {
        if (threadId == 0L) emptyList() else dao.forThread(threadId)
    }

    suspend fun rowsForAddress(address: String): List<LocalMessageEntity> = withContext(Dispatchers.IO) {
        dao.forAddress(addressKey(address))
    }

    suspend fun forAddress(address: String): List<Message> = withContext(Dispatchers.IO) {
        dao.forAddress(addressKey(address)).map { toMessage(it) }
    }

    /** Every archived row, newest first; used to build conversations the provider does not know. */
    suspend fun snapshot(): List<LocalMessageEntity> = withContext(Dispatchers.IO) { dao.snapshot() }

    suspend fun get(id: Long): LocalMessageEntity? = dao.get(id)

    /**
     * Records an outgoing message before it is sent. [threadId] may be <= 0 when the provider
     * has not allocated a thread yet; the row is re-pointed once it does.
     */
    suspend fun recordOutgoing(
        address: String,
        body: String,
        threadId: Long,
        subscriptionId: Int,
        timestamp: Long = System.currentTimeMillis(),
        /** When supplied, the archive row reuses the outbox id so the two rows stay in sync. */
        rowId: Long = 0,
    ): Long = withContext(Dispatchers.IO) {
        val key = addressKey(address)
        val effectiveThread = if (threadId > 0) threadId else syntheticThreadId(key)
        val id = dao.insert(
            LocalMessageEntity(
                id = rowId,
                threadId = effectiveThread,
                address = address,
                addressKey = key,
                body = body,
                direction = Direction.OUTGOING.name,
                status = MessageStatus.SENDING.name,
                timestamp = timestamp,
                subscriptionId = subscriptionId,
                read = true,
                createdAt = timestamp,
            )
        )
        changed()
        id
    }

    /**
     * Records an incoming message. [threadId] may be <= 0 when the provider insert failed; the
     * message is still kept so the user never loses an SMS because a role was revoked.
     */
    suspend fun recordIncoming(
        address: String,
        body: String,
        threadId: Long,
        subscriptionId: Int,
        timestamp: Long,
        read: Boolean = false,
        systemMessageId: Long? = null,
        providerUri: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val key = addressKey(address)
        val effectiveThread = if (threadId > 0) threadId else syntheticThreadId(key)
        val id = dao.insert(
            LocalMessageEntity(
                threadId = effectiveThread,
                address = address,
                addressKey = key,
                body = body,
                direction = Direction.INCOMING.name,
                status = MessageStatus.RECEIVED.name,
                timestamp = timestamp,
                subscriptionId = subscriptionId,
                systemMessageId = systemMessageId,
                providerUri = providerUri,
                read = read,
                createdAt = System.currentTimeMillis(),
            )
        )
        changed()
        id
    }

    suspend fun markSent(id: Long, systemMessageId: Long?, providerUri: String?) =
        withContext(Dispatchers.IO) {
            dao.updateStatus(id, MessageStatus.SENT.name, null, true)
            // Only overwrite the provider link when we actually have one: the send result
            // arrives after the insert, and passing null here used to erase the link.
            if (systemMessageId != null || providerUri != null) {
                dao.attachSystemMessage(id, systemMessageId, providerUri)
            }
            changed()
        }

    suspend fun markDelivered(id: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, MessageStatus.DELIVERED.name, null, true)
        changed()
    }

    suspend fun markFailed(id: Long, reason: Int?) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, MessageStatus.FAILED.name, reason, true)
        changed()
    }

    suspend fun markQueued(id: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, MessageStatus.SENDING.name, null, true)
        changed()
    }

    /** Links the archived row to the provider row created for the same send. */
    suspend fun attachSystemMessage(id: Long, systemMessageId: Long?, providerUri: String?) =
        withContext(Dispatchers.IO) {
            dao.attachSystemMessage(id, systemMessageId, providerUri)
            changed()
        }

    /** Moves rows from a synthetic thread id to the real provider thread id. */
    suspend fun adoptThread(oldThreadId: Long, newThreadId: Long) = withContext(Dispatchers.IO) {
        if (oldThreadId == newThreadId || newThreadId <= 0) return@withContext
        dao.repointThread(oldThreadId, newThreadId)
        changed()
    }

    suspend fun setThread(id: Long, threadId: Long) = withContext(Dispatchers.IO) {
        if (threadId > 0) dao.setThread(id, threadId)
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        if (threadId != 0L) dao.markThreadRead(threadId)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.delete(id)
        changed()
    }

    suspend fun deleteThread(threadId: Long) = withContext(Dispatchers.IO) {
        if (threadId != 0L) dao.deleteThread(threadId)
        changed()
    }

    suspend fun search(query: String, limit: Int = 80): List<Message> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        dao.search("%${query.trim()}%", limit).map { toMessage(it) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }

    suspend fun pruneMirrored(olderThan: Long) = withContext(Dispatchers.IO) {
        dao.pruneMirrored(olderThan)
    }

    fun addressKey(address: String): String = PhoneNumbers.comparisonKey(address)

    /**
     * Stable negative thread id for a conversation the provider has no id for.
     * Negative ids are never produced by Android's provider, so the two id spaces cannot clash.
     */
    fun syntheticThreadId(addressKey: String): Long {
        if (addressKey.isEmpty()) return SYNTHETIC_BASE - 1
        return SYNTHETIC_BASE - abs(addressKey.hashCode() % 100_000_000)
    }

    fun isSyntheticThread(threadId: Long): Boolean = threadId <= SYNTHETIC_BASE

    /** Maps an archive row into the UI message model (ids are offset, see [archiveRowId]). */
    fun toMessage(row: LocalMessageEntity): Message = Message(
        id = ARCHIVE_ID_BASE - row.id,
        source = MessageSource.SMS,
        threadId = row.threadId,
        address = row.address,
        body = row.body,
        timestamp = row.timestamp,
        direction = runCatching { Direction.valueOf(row.direction) }.getOrDefault(Direction.OUTGOING),
        status = runCatching { MessageStatus.valueOf(row.status) }.getOrDefault(MessageStatus.UNKNOWN),
        read = row.read,
        starred = false,
        subscriptionId = row.subscriptionId,
        errorCode = row.errorCode,
    )

    /** Recovers the archive row id from a [Message] produced by [toMessage]. */
    fun archiveRowId(message: Message): Long? =
        if (message.id <= ARCHIVE_ID_BASE) ARCHIVE_ID_BASE - message.id else null

    companion object {
        /** Archive ids are mapped into this band so they never collide with provider ids. */
        const val ARCHIVE_ID_BASE = -1_000_000_000L

        /** Lower bound for synthetic thread ids (real provider threads are always positive). */
        const val SYNTHETIC_BASE = -2_000_000_000_000L
    }
}
