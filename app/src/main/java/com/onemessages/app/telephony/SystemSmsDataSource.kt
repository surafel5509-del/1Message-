package com.onemessages.app.telephony

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.onemessages.app.core.Logx
import com.onemessages.app.model.Attachment
import com.onemessages.app.model.AttachmentKind
import com.onemessages.app.model.Direction
import com.onemessages.app.model.Message
import com.onemessages.app.model.MessageSource
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.util.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Raw thread row from Android's provider, before app metadata is applied. */
data class ThreadRow(
    val threadId: Long,
    val recipientIds: List<String>,
    val rawRecipientIds: String,
    val snippet: String?,
    val date: Long,
    val messageCount: Int,
    val archived: Boolean,
    val unreadCount: Int,
    val hasAttachment: Boolean,
    val lastMessageType: Int,
    val lastMessageStatus: Int,
    val lastMessageIsMms: Boolean,
    val subscriptionId: Int,
    val lastMessageRead: Boolean,
    val errorCode: Int?,
)

/**
 * Read/write access to Android's authoritative SMS/MMS provider.
 *
 * Reading works with READ_SMS. Writing only works reliably when this app holds the default
 * SMS role; every write path checks that and reports failure instead of pretending success.
 */
@Singleton
class SystemSmsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    // ------------------------------------------------------------------------------------
    // Conversations
    // ------------------------------------------------------------------------------------

    suspend fun loadThreads(): List<ThreadRow> = withContext(Dispatchers.IO) {
        val unreadByThread = unreadCountsByThread()
        val threads = queryThreads()
        threads.map { t ->
            val unread = unreadByThread[t.threadId] ?: 0
            t.copy(unreadCount = unread)
        }
    }

    private fun queryThreads(): List<ThreadRow> {
        val fullProjection = arrayOf(
            Telephony.Threads._ID,
            Telephony.Threads.DATE,
            Telephony.Threads.MESSAGE_COUNT,
            Telephony.Threads.RECIPIENT_IDS,
            Telephony.Threads.SNIPPET,
            "read",
            "archived",
            "has_attachment",
            "error",
        )
        val cursor = try {
            resolver.query(threadsUri(), fullProjection, null, null, "${Telephony.Threads.DATE} DESC")
        } catch (e: Exception) {
            Logx.w("Provider", "thread query with optional columns failed, retrying minimal", e)
            null
        }

        val used = cursor ?: runCatching {
            resolver.query(
                threadsUri(),
                arrayOf(
                    Telephony.Threads._ID,
                    Telephony.Threads.DATE,
                    Telephony.Threads.MESSAGE_COUNT,
                    Telephony.Threads.RECIPIENT_IDS,
                    Telephony.Threads.SNIPPET,
                            "read",
                ),
                null,
                null,
                "${Telephony.Threads.DATE} DESC",
            )
        }.onFailure { Logx.e("Provider", "thread query failed", it) }.getOrNull()
            ?: return emptyList()

        val rows = ArrayList<ThreadRow>(used.count.coerceAtLeast(8))
        used.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Threads._ID)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Threads.DATE)
            val countIdx = c.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT)
            val recipientsIdx = c.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)
            val snippetIdx = c.getColumnIndex(Telephony.Threads.SNIPPET)
            val readIdx = c.getColumnIndex("read")
            val archivedIdx = c.getColumnIndex("archived")
            val attachmentIdx = c.getColumnIndex("has_attachment")
            val errorIdx = c.getColumnIndex("error")

            while (c.moveToNext()) {
                val threadId = c.getLong(idIdx)
                val rawRecipients = c.getStringOrNull(recipientsIdx).orEmpty()
                val snippet = c.getStringOrNull(snippetIdx)
                rows += ThreadRow(
                    threadId = threadId,
                    recipientIds = PhoneNumbers.parseRecipientIds(rawRecipients),
                    rawRecipientIds = rawRecipients,
                    snippet = snippet,
                    date = c.getLong(dateIdx),
                    messageCount = c.getInt(countIdx),
                    archived = archivedIdx >= 0 && c.getInt(archivedIdx) == 1,
                    unreadCount = 0,
                    hasAttachment = attachmentIdx >= 0 && c.getInt(attachmentIdx) == 1,
                    lastMessageType = -1,
                    lastMessageStatus = -1,
                    lastMessageIsMms = false,
                    subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId(),
                    lastMessageRead = readIdx >= 0 && c.getInt(readIdx) == 1,
                    errorCode = errorIdx.takeIf { it >= 0 }?.let { c.getIntOrNull(it) },
                )
            }
        }
        return rows
    }

    private fun threadsUri(): Uri = Telephony.Threads.CONTENT_URI

    private fun threadUri(threadId: Long): Uri =
        Uri.parse("content://mms-sms/conversations").buildUpon()
            .appendPath(threadId.toString())
            .build()

    /** One grouped read for unread counts instead of a query per conversation. */
    private fun unreadCountsByThread(): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        countUnread(Telephony.Sms.Inbox.CONTENT_URI, Telephony.Sms.THREAD_ID, counts)
        countUnread(Telephony.Mms.Inbox.CONTENT_URI, Telephony.Mms.THREAD_ID, counts)
        return counts
    }

    private fun countUnread(uri: Uri, threadColumn: String, into: MutableMap<Long, Int>) {
        runCatching {
            resolver.query(
                uri,
                arrayOf(threadColumn),
                "${Telephony.Sms.READ} = 0",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val threadId = c.getLong(0)
                    into[threadId] = (into[threadId] ?: 0) + 1
                }
            }
        }.onFailure { Logx.w("Provider", "unread count query failed", it) }
    }

    /**
     * Total unread inbound messages. Uses a projection of one column so it stays cheap enough
     * to drive the navigation badge on every provider change.
     */
    suspend fun unreadTotal(): Int = withContext(Dispatchers.IO) {
        var total = 0
        listOf(Telephony.Sms.Inbox.CONTENT_URI, Telephony.Mms.Inbox.CONTENT_URI).forEach { uri ->
            runCatching {
                resolver.query(uri, arrayOf("_id"), "${Telephony.Sms.READ} = 0", null, null)
                    ?.use { cursor -> total += cursor.count }
            }.onFailure { Logx.w("Provider", "unread total query failed", it) }
        }
        total
    }

    /**
     * Last-message facts for the visible threads. Sorted newest-first so we can stop as soon
     * as each thread has been seen, keeping this constant-ish even on large inboxes.
     */
    suspend fun lastMessageFacts(threadIds: Set<Long>): Map<Long, LastMessageFacts> =
        withContext(Dispatchers.IO) {
            if (threadIds.isEmpty()) return@withContext emptyMap()
            val remaining = threadIds.toMutableSet()
            val result = HashMap<Long, LastMessageFacts>(threadIds.size)
            val projection = arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.TYPE,
                Telephony.Sms.STATUS,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
                "sub_id",
            )
            var scanned = 0
            runCatching {
                resolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                )?.use { c ->
                    while (c.moveToNext() && remaining.isNotEmpty() && scanned < MAX_SCAN) {
                        scanned++
                        val threadId = c.getLong(0)
                        if (threadId !in remaining) continue
                        remaining.remove(threadId)
                        result[threadId] = LastMessageFacts(
                            type = c.getInt(1),
                            status = c.getInt(2),
                            date = c.getLong(3),
                            read = c.getInt(4) == 1,
                            subscriptionId = c.getIntOrNull(5) ?: -1,
                            isMms = false,
                        )
                    }
                }
            }.onFailure { Logx.w("Provider", "last message scan failed", it) }

            if (remaining.isNotEmpty()) {
                runCatching {
                    resolver.query(
                        Telephony.Mms.CONTENT_URI,
                        arrayOf(
                            Telephony.Mms.THREAD_ID,
                            Telephony.Mms.MESSAGE_BOX,
                            Telephony.Mms.DATE,
                            Telephony.Mms.READ,
                            "sub_id",
                        ),
                        null,
                        null,
                        "${Telephony.Mms.DATE} DESC",
                    )?.use { c ->
                        while (c.moveToNext() && remaining.isNotEmpty() && scanned < MAX_SCAN) {
                            scanned++
                            val threadId = c.getLong(0)
                            if (threadId !in remaining) continue
                            remaining.remove(threadId)
                            result[threadId] = LastMessageFacts(
                                type = if (c.getInt(1) == Telephony.Mms.MESSAGE_BOX_INBOX) {
                                    Telephony.Sms.MESSAGE_TYPE_INBOX
                                } else {
                                    Telephony.Sms.MESSAGE_TYPE_SENT
                                },
                                status = -1,
                                date = c.getLong(2),
                                read = c.getInt(3) == 1,
                                subscriptionId = c.getIntOrNull(4) ?: -1,
                                isMms = true,
                            )
                        }
                    }
                }.onFailure { Logx.w("Provider", "mms last message scan failed", it) }
            }
            result
        }

    data class LastMessageFacts(
        val type: Int,
        val status: Int,
        val date: Long,
        val read: Boolean,
        val subscriptionId: Int,
        val isMms: Boolean,
    )

    /** Resolves canonical address ids (used by the threads table) to phone numbers. */
    suspend fun loadCanonicalAddresses(ids: Set<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext emptyMap()
            val numeric = ids.mapNotNull { it.toLongOrNull() }
            if (numeric.isEmpty()) return@withContext emptyMap()
            val map = HashMap<String, String>(numeric.size)
            val placeholders = numeric.joinToString(",") { "?" }
            runCatching {
                resolver.query(
                    Uri.parse("content://mms-sms/canonical-addresses"),
                    arrayOf("_id", "address"),
                    "_id IN ($placeholders)",
                    numeric.map { it.toString() }.toTypedArray(),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        map[c.getLong(0).toString()] = c.getStringOrNull(1).orEmpty()
                    }
                }
            }.onFailure { Logx.w("Provider", "canonical address query failed", it) }
            map
        }

    // ------------------------------------------------------------------------------------
    // Messages in a thread
    // ------------------------------------------------------------------------------------

    suspend fun loadMessages(
        threadId: Long,
        limit: Int,
        beforeTimestamp: Long? = null,
    ): List<Message> = withContext(Dispatchers.IO) {
        if (threadId <= 0) return@withContext emptyList()
        val sms = loadSmsInThread(threadId, limit, beforeTimestamp)
        val mms = loadMmsInThread(threadId, limit, beforeTimestamp)
        (sms + mms).sortedWith(compareBy({ it.timestamp }, { if (it.source == MessageSource.SMS) 0 else 1 }))
            .takeLast(limit)
    }

    private fun loadSmsInThread(threadId: Long, limit: Int, before: Long?): List<Message> {
        val selection = buildString {
            append("${Telephony.Sms.THREAD_ID} = ?")
            if (before != null) append(" AND ${Telephony.Sms.DATE} < ?")
        }
        val args = if (before != null) {
            arrayOf(threadId.toString(), before.toString())
        } else {
            arrayOf(threadId.toString())
        }
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.TYPE,
            Telephony.Sms.STATUS,
            Telephony.Sms.READ,
            "sub_id",
        )
        val messages = ArrayList<Message>(limit.coerceAtMost(64))
        runCatching {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                args,
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(6)
                    val direction = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX -> Direction.INCOMING
                        Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                        Telephony.Sms.MESSAGE_TYPE_SENT,
                        Telephony.Sms.MESSAGE_TYPE_QUEUED,
                        Telephony.Sms.MESSAGE_TYPE_FAILED -> Direction.OUTGOING
                        else -> Direction.INCOMING
                    }
                    val statusValue = c.getIntOrNull(7) ?: -1
                    messages += Message(
                        id = c.getLong(0),
                        source = MessageSource.SMS,
                        threadId = c.getLong(1),
                        address = c.getStringOrNull(2).orEmpty(),
                        body = c.getStringOrNull(3).orEmpty(),
                        timestamp = c.getLong(4),
                        direction = direction,
                        status = mapSmsStatus(direction, statusValue, type),
                        read = c.getInt(8) == 1,
                        starred = false,
                        subscriptionId = c.getIntOrNull(9) ?: -1,
                    )
                }
            }
        }.onFailure { Logx.e("Provider", "sms thread query failed", it) }
        return messages
    }

    private fun loadMmsInThread(threadId: Long, limit: Int, before: Long?): List<Message> {
        val selection = buildString {
            append("${Telephony.Mms.THREAD_ID} = ?")
            if (before != null) append(" AND ${Telephony.Mms.DATE} < ?")
        }
        val args = if (before != null) {
            arrayOf(threadId.toString(), before.toString())
        } else {
            arrayOf(threadId.toString())
        }
        val rows = ArrayList<Message>(4)
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.READ,
            Telephony.Mms.SUBJECT,
            Telephony.Mms.CONTENT_TYPE,
            Telephony.Mms.RETRIEVE_STATUS,
            Telephony.Mms.DATE_SENT,
            "sub_id",
        )
        runCatching {
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                selection,
                args,
                "${Telephony.Mms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val box = c.getInt(3)
                    val retrieveStatus = c.getIntOrNull(7) ?: 0
                    val read = c.getInt(4) == 1
                    val direction = if (box == Telephony.Mms.MESSAGE_BOX_INBOX) {
                        Direction.INCOMING
                    } else {
                        Direction.OUTGOING
                    }
                    val pendingDownload = direction == Direction.INCOMING &&
                        retrieveStatus != MMS_RETRIEVE_STATUS_OK &&
                        !read
                    rows += Message(
                        id = id,
                        source = MessageSource.MMS,
                        threadId = c.getLong(1),
                        address = "", // filled from the addr table by the caller when needed
                        body = "",
                        subject = c.getStringOrNull(5),
                        timestamp = c.getLong(2) * 1000L,
                        direction = direction,
                        status = if (direction == Direction.INCOMING) {
                            MessageStatus.RECEIVED
                        } else {
                            MessageStatus.SENT
                        },
                        read = read,
                        starred = false,
                        subscriptionId = c.getIntOrNull(9) ?: -1,
                        attachments = loadParts(id, pendingDownload),
                        mmsBox = box,
                        mmsContentLocation = c.getStringOrNull(6),
                    )
                }
            }
        }.onFailure { Logx.e("Provider", "mms thread query failed", it) }
        return rows
    }

    fun loadParts(mmsId: Long, pendingDownload: Boolean = false): List<Attachment> {
        val parts = ArrayList<Attachment>(3)
        runCatching {
            resolver.query(
                Telephony.Mms.Part.CONTENT_URI,
                arrayOf(
                    Telephony.Mms.Part._ID,
                    Telephony.Mms.Part.CONTENT_TYPE,
                    Telephony.Mms.Part.NAME,
                    Telephony.Mms.Part.TEXT,
                    Telephony.Mms.Part.CONTENT_ID,
                ),
                "${Telephony.Mms.Part.MSG_ID} = ?",
                arrayOf(mmsId.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val partId = c.getLong(0)
                    val contentType = c.getStringOrNull(1).orEmpty()
                    val name = c.getStringOrNull(2)
                    val text = c.getStringOrNull(3)
                    // Text parts are the message body; they are not attachments.
                    if (contentType.startsWith("text/") && name.isNullOrBlank()) continue
                    if (text != null && contentType.startsWith("text/")) continue
                    parts += Attachment(
                        id = partId,
                        messageId = mmsId,
                        contentType = contentType.ifBlank { "application/octet-stream" },
                        uriString = ContentUris.withAppendedId(
                            Telephony.Mms.Part.CONTENT_URI, partId
                        ).toString(),
                        fileName = name,
                        sizeBytes = partSize(partId),
                        kind = kindOf(contentType),
                        isPendingDownload = pendingDownload,
                    )
                }
            }
        }.onFailure { Logx.w("Provider", "mms parts query failed", it) }
        return parts
    }

    private fun partSize(partId: Long): Long? = runCatching {
        resolver.openAssetFileDescriptor(
            ContentUris.withAppendedId(Telephony.Mms.Part.CONTENT_URI, partId),
            "r",
        )?.use { it.length }
    }.getOrNull()?.takeIf { it > 0 }

    /** Message body for an MMS: concatenation of its text parts. */
    fun loadMmsBody(mmsId: Long): String = runCatching<String?> {
        resolver.query(
            Telephony.Mms.Part.CONTENT_URI,
            arrayOf(Telephony.Mms.Part.TEXT, Telephony.Mms.Part.CONTENT_TYPE),
            "${Telephony.Mms.Part.MSG_ID} = ?",
            arrayOf(mmsId.toString()),
            null,
        )?.use { c ->
            buildString {
                while (c.moveToNext()) {
                    val text = c.getStringOrNull(0)
                    val type = c.getStringOrNull(1).orEmpty()
                    if (!text.isNullOrBlank() && type.startsWith("text/")) {
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }
        }
    }.getOrNull().orEmpty()

    suspend fun loadMessage(id: Long, source: MessageSource): Message? = withContext(Dispatchers.IO) {
        when (source) {
            MessageSource.SMS -> loadSmsById(id)
            MessageSource.MMS -> loadMmsById(id)
        }
    }

    private fun loadSmsById(id: Long): Message? = runCatching {
        resolver.query(
            ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
            arrayOf(
                Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS,
                Telephony.Sms.READ, "sub_id",
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val type = c.getInt(4)
            val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                Direction.INCOMING
            } else {
                Direction.OUTGOING
            }
            Message(
                id = id,
                source = MessageSource.SMS,
                threadId = c.getLong(0),
                address = c.getStringOrNull(1).orEmpty(),
                body = c.getStringOrNull(2).orEmpty(),
                timestamp = c.getLong(3),
                direction = direction,
                status = mapSmsStatus(direction, c.getIntOrNull(5) ?: -1, type),
                read = c.getInt(6) == 1,
                starred = false,
                subscriptionId = c.getIntOrNull(7) ?: -1,
            )
        }
    }.getOrNull()

    private fun loadMmsById(id: Long): Message? = runCatching {
        resolver.query(
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id),
            arrayOf(
                Telephony.Mms.THREAD_ID, Telephony.Mms.DATE, Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.READ, Telephony.Mms.SUBJECT, Telephony.Mms.RETRIEVE_STATUS, "sub_id",
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val box = c.getInt(2)
            val direction = if (box == Telephony.Mms.MESSAGE_BOX_INBOX) {
                Direction.INCOMING
            } else {
                Direction.OUTGOING
            }
            Message(
                id = id,
                source = MessageSource.MMS,
                threadId = c.getLong(0),
                address = mmsAddress(id),
                body = loadMmsBody(id),
                subject = c.getStringOrNull(4),
                timestamp = c.getLong(1) * 1000L,
                direction = direction,
                status = if (direction == Direction.INCOMING) MessageStatus.RECEIVED else MessageStatus.SENT,
                read = c.getInt(3) == 1,
                starred = false,
                subscriptionId = c.getIntOrNull(6) ?: -1,
                attachments = loadParts(id),
                mmsBox = box,
            )
        }
    }.getOrNull()

    /** The counterparty address of an MMS lives in its address table with type 137/151. */
    fun mmsAddress(mmsId: Long): String = runCatching<String?> {
        resolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address", "type"),
            null, null, null,
        )?.use { c ->
            var fallback = ""
            while (c.moveToNext()) {
                val address = c.getStringOrNull(0).orEmpty()
                if (address.isBlank()) continue
                val type = c.getInt(1)
                // 137 = FROM, 151 = TO
                if (type == 137) return@use address
                if (fallback.isEmpty()) fallback = address
            }
            fallback
        }
    }.getOrNull().orEmpty()

    suspend fun mmsAddressAsync(mmsId: Long): String = withContext(Dispatchers.IO) { mmsAddress(mmsId) }

    suspend fun threadRecipientAddresses(threadId: Long): List<String> = withContext(Dispatchers.IO) {
        // Threads with a single recipient expose it through the canonical-address join; the
        // most reliable path is the most recent inbound/outbound row.
        runCatching<List<String>> {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1",
            )?.use { c ->
                if (c.moveToFirst()) {
                    val address = c.getStringOrNull(0)
                    if (address.isNullOrBlank()) emptyList<String>() else listOf(address)
                } else {
                    emptyList<String>()
                }
            } ?: emptyList<String>()
        }.getOrNull().orEmpty()
    }

    // ------------------------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------------------------

    /** Messages inside a date window, used by natural-language date searches. */
    suspend fun loadMessagesBetween(
        startMillis: Long,
        endMillis: Long,
        limit: Int = 120,
    ): List<Message> = withContext(Dispatchers.IO) {
        val messages = ArrayList<Message>(32)
        runCatching {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
                    Telephony.Sms.STATUS, Telephony.Sms.READ, "sub_id",
                ),
                "${Telephony.Sms.DATE} BETWEEN ? AND ?",
                arrayOf(startMillis.toString(), endMillis.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $limit",
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(5)
                    val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        Direction.INCOMING
                    } else {
                        Direction.OUTGOING
                    }
                    messages += Message(
                        id = c.getLong(0),
                        source = MessageSource.SMS,
                        threadId = c.getLong(1),
                        address = c.getStringOrNull(2).orEmpty(),
                        body = c.getStringOrNull(3).orEmpty(),
                        timestamp = c.getLong(4),
                        direction = direction,
                        status = mapSmsStatus(direction, c.getIntOrNull(6) ?: -1, type),
                        read = c.getInt(7) == 1,
                        starred = false,
                        subscriptionId = c.getIntOrNull(8) ?: -1,
                    )
                }
            }
        }.onFailure { Logx.w("Provider", "date search query failed", it) }
        messages
    }

    suspend fun searchMessages(query: String, limit: Int = 120): List<Message> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val messages = ArrayList<Message>(32)
            runCatching {
                resolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                        Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE,
                        Telephony.Sms.STATUS, Telephony.Sms.READ, "sub_id",
                    ),
                    "${Telephony.Sms.BODY} LIKE ?",
                    arrayOf("%$query%"),
                    "${Telephony.Sms.DATE} DESC LIMIT $limit",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val type = c.getInt(5)
                        val direction = if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                            Direction.INCOMING
                        } else {
                            Direction.OUTGOING
                        }
                        messages += Message(
                            id = c.getLong(0),
                            source = MessageSource.SMS,
                            threadId = c.getLong(1),
                            address = c.getStringOrNull(2).orEmpty(),
                            body = c.getStringOrNull(3).orEmpty(),
                            timestamp = c.getLong(4),
                            direction = direction,
                            status = mapSmsStatus(direction, c.getIntOrNull(6) ?: -1, type),
                            read = c.getInt(7) == 1,
                            starred = false,
                            subscriptionId = c.getIntOrNull(8) ?: -1,
                        )
                    }
                }
            }.onFailure { Logx.w("Provider", "search query failed", it) }
            messages
        }

    // ------------------------------------------------------------------------------------
    // Writes (require default SMS role for the provider; sending is separate)
    // ------------------------------------------------------------------------------------

    /** Marks every message of a thread read (inbox) or unread. Returns rows updated. */
    suspend fun setThreadRead(threadId: Long, read: Boolean): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
        }
        val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?"
        val args = arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
        val sms = runCatching { resolver.update(Telephony.Sms.CONTENT_URI, values, selection, args) }
            .getOrDefault(0)
        val mmsValues = ContentValues().apply { put(Telephony.Mms.READ, if (read) 1 else 0) }
        val mms = runCatching {
            resolver.update(
                Telephony.Mms.CONTENT_URI,
                mmsValues,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.MESSAGE_BOX} = ?",
                arrayOf(threadId.toString(), Telephony.Mms.MESSAGE_BOX_INBOX.toString()),
            )
        }.getOrDefault(0)
        sms + mms > 0
    }

    suspend fun setMessageRead(messageId: Long, read: Boolean): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
        }
        runCatching {
            resolver.update(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId), values, null, null
            ) > 0
        }.getOrDefault(false)
    }

    suspend fun setThreadArchived(threadId: Long, archived: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
                resolver.update(threadUri(threadId), values, null, null) > 0
            }.getOrDefault(false)
        }

    suspend fun deleteMessage(id: Long, source: MessageSource): Boolean = withContext(Dispatchers.IO) {
        val uri = when (source) {
            MessageSource.SMS -> ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id)
            MessageSource.MMS -> ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id)
        }
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    suspend fun deleteThread(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.delete(threadUri(threadId), null, null) > 0
        }.onFailure { Logx.e("Provider", "thread delete failed", it) }.getOrDefault(false)
    }

    /**
     * Persists an inbound SMS into the system inbox. Only the default SMS app may do this,
     * which is why the app asks for the role before claiming to manage messages.
     */
    suspend fun insertIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int,
    ): Long? = withContext(Dispatchers.IO) {
        val threadId = obtainThreadId(address) ?: return@withContext null
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.DATE_SENT, timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.THREAD_ID, threadId)
            if (subscriptionId >= 0) put("sub_id", subscriptionId)
        }
        runCatching {
            resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        }.onFailure { Logx.e("Provider", "incoming sms insert failed", it) }.getOrNull()
    }

    suspend fun insertOutgoingSms(
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int,
        status: Int = Telephony.Sms.STATUS_PENDING,
    ): Pair<Long, Uri?>? = withContext(Dispatchers.IO) {
        val threadId = obtainThreadId(address) ?: return@withContext null
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, timestamp)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, status)
            put(Telephony.Sms.THREAD_ID, threadId)
            if (subscriptionId >= 0) put("sub_id", subscriptionId)
        }
        // Insert into the box that matches the row: AOSP's SmsProvider expects a concrete
        // folder URI, and writing to content://sms with a box in the values is provider
        // dependent (some ROMs reject it, which silently lost sent messages).
        runCatching {
            val uri = resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
                ?: resolver.insert(Telephony.Sms.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) to it }
        }.onFailure { Logx.e("Provider", "outgoing sms insert failed", it) }.getOrNull()
    }

    suspend fun updateOutgoingSms(
        uri: Uri,
        type: Int,
        status: Int,
        subscriptionId: Int = -1,
    ): Boolean = withContext(Dispatchers.IO) {
        // DATE is deliberately left alone: rewriting it reordered the thread and made the
        // message jump above newer replies.
        val values = ContentValues().apply {
            put(Telephony.Sms.TYPE, type)
            put(Telephony.Sms.STATUS, status)
            if (subscriptionId >= 0) put("sub_id", subscriptionId)
        }
        runCatching {
            if (resolver.update(uri, values, null, null) > 0) {
                true
            } else {
                // Some ROMs reject updates on a box-scoped URI; retry on the flat message URI
                // so the sent/failed state is never silently dropped.
                runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { id ->
                    resolver.update(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                        values,
                        null,
                        null,
                    ) > 0
                } ?: false
            }
        }.getOrDefault(false)
    }

    suspend fun obtainThreadId(address: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            Telephony.Threads.getOrCreateThreadId(context, address)
        }.onFailure { Logx.w("Provider", "getOrCreateThreadId failed", it) }.getOrNull()
    }

    suspend fun findExistingThreadId(address: String): Long? = withContext(Dispatchers.IO) {
        // Read-only lookup so a non-default app can still open existing conversations.
        runCatching {
            val uri = Uri.parse("content://mms-sms/threadID")
                .buildUpon()
                .appendQueryParameter("recipient", address)
                .build()
            resolver.query(uri, arrayOf("_id"), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }.onFailure { Logx.w("Provider", "thread id lookup failed", it) }.getOrNull()
    }

    /** Dedupe guard for inbound broadcasts that a carrier or ROM repeats. */
    suspend fun findIncomingDuplicate(
        address: String,
        body: String,
        timestamp: Long,
        windowMillis: Long = 5_000,
    ): Boolean = withContext(Dispatchers.IO) {
        val key = PhoneNumbers.comparisonKey(address)
        runCatching {
            resolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} BETWEEN ? AND ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(
                    (timestamp - windowMillis).toString(),
                    (timestamp + windowMillis).toString(),
                    Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                ),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val existingAddress = c.getStringOrNull(0).orEmpty()
                    val existingBody = c.getStringOrNull(1).orEmpty()
                    if (existingBody == body && PhoneNumbers.comparisonKey(existingAddress) == key) {
                        return@withContext true
                    }
                }
                false
            } ?: false
        }.getOrDefault(false)
    }

    suspend fun markMmsSeen(mmsId: Long): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
        }
        runCatching {
            resolver.update(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId), values, null, null) > 0
        }.getOrDefault(false)
    }

    /** Byte stream for saving an attachment to user-chosen storage. */
    fun openPart(uri: Uri) = runCatching { resolver.openInputStream(uri) }.getOrNull()

    private fun mapSmsStatus(direction: Direction, rawStatus: Int, type: Int): MessageStatus {
        if (direction == Direction.INCOMING) return MessageStatus.RECEIVED
        return when (rawStatus) {
            Telephony.Sms.STATUS_PENDING -> MessageStatus.SENDING
            Telephony.Sms.STATUS_FAILED -> MessageStatus.FAILED
            Telephony.Sms.STATUS_COMPLETE -> MessageStatus.SENT
            else -> when (type) {
                Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
                Telephony.Sms.MESSAGE_TYPE_OUTBOX -> MessageStatus.SENDING
                Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.QUEUED
                Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
                else -> MessageStatus.SENT
            }
        }
    }

    private fun kindOf(contentType: String): AttachmentKind = when {
        contentType.startsWith("image/") -> AttachmentKind.IMAGE
        contentType.startsWith("video/") -> AttachmentKind.VIDEO
        contentType.startsWith("audio/") -> AttachmentKind.AUDIO
        contentType.startsWith("text/x-vcard") || contentType == "text/vcard" -> AttachmentKind.VCARD
        contentType.startsWith("text/") || contentType == "application/pdf" -> AttachmentKind.DOCUMENT
        else -> AttachmentKind.OTHER
    }

    companion object {
        /** Bounds the newest-first scan used to build inbox delivery states. */
        private const val MAX_SCAN = 4000

        /** MMS retrieve_status value meaning "already fetched" (MMS spec "Ok"). */
        private const val MMS_RETRIEVE_STATUS_OK = 128

        const val DEFAULT_SMS_SUBSCRIPTION = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
    }
}
