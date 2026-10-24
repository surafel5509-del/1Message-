package com.onemessages.app.data.repository

import com.onemessages.app.contacts.ContactsRepository
import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.ConversationMetaDao
import com.onemessages.app.data.local.ConversationMetaEntity
import com.onemessages.app.data.local.DraftDao
import com.onemessages.app.data.local.DraftEntity
import com.onemessages.app.data.local.OutboxDao
import com.onemessages.app.data.local.StarredMessageDao
import com.onemessages.app.data.local.StarredMessageEntity
import com.onemessages.app.data.preferences.AppSettings
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.domain.SmartOrganizer
import com.onemessages.app.model.CategoryRule
import com.onemessages.app.model.Conversation
import com.onemessages.app.model.ConversationCategory
import com.onemessages.app.model.ConversationMetadata
import com.onemessages.app.model.ConversationSort
import com.onemessages.app.model.Direction
import com.onemessages.app.model.InboxFilter
import com.onemessages.app.model.Message
import com.onemessages.app.model.MessageSource
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.model.OutboxEntry
import com.onemessages.app.model.OutboxState
import com.onemessages.app.model.SearchHit
import com.onemessages.app.telephony.SubscriptionRepository
import com.onemessages.app.telephony.SystemSmsDataSource
import com.onemessages.app.util.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class MessagePage(
    val messages: List<Message>,
    val hasMore: Boolean,
)

data class SearchResults(
    val messages: List<SearchHit>,
    val conversations: List<Conversation>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ConversationRepository @Inject constructor(
    private val provider: SystemSmsDataSource,
    private val metaDao: ConversationMetaDao,
    private val draftDao: DraftDao,
    private val starredDao: StarredMessageDao,
    private val outboxDao: OutboxDao,
    private val contacts: ContactsRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: ProviderChangeNotifier,
    private val subscriptions: SubscriptionRepository,
    private val organization: OrganizationRepository,
) {

    private data class MetadataBundle(
        val meta: List<ConversationMetaEntity>,
        val drafts: List<DraftEntity>,
        val starred: List<StarredMessageEntity>,
        val settings: AppSettings,
    )

    private val changeStream = notifier.changes.onStart { emit(Unit) }

    private val metadataFlow: Flow<MetadataBundle> = combine(
        metaDao.observeAll(),
        draftDao.observeAll(),
        starredDao.observeAll(),
        settingsRepository.settings,
    ) { meta, drafts, starred, settings ->
        MetadataBundle(meta, drafts, starred, settings)
    }

    // ------------------------------------------------------------------------------------
    // Inbox
    // ------------------------------------------------------------------------------------

    fun observeInbox(
        filter: InboxFilter,
        sort: ConversationSort = ConversationSort.RECENT,
    ): Flow<List<Conversation>> =
        combine(changeStream, metadataFlow) { _, bundle -> bundle }
            .mapLatest { bundle -> withContext(Dispatchers.IO) { buildInbox(filter, sort, bundle) } }
            .flowOn(Dispatchers.IO)

    /** Used by the starred tab: conversations with a starred flag or starred message. */
    fun observeStarred(): Flow<List<Conversation>> =
        combine(changeStream, metadataFlow) { _, bundle -> bundle }
            .mapLatest { bundle ->
                withContext(Dispatchers.IO) {
                    val starredThreads = bundle.starred.map { it.threadId }.toSet()
                    buildInbox(InboxFilter.STARRED, ConversationSort.RECENT, bundle)
                        .filter { it.isStarred || it.threadId in starredThreads }
                }
            }
            .flowOn(Dispatchers.IO)

    private suspend fun buildInbox(
        filter: InboxFilter,
        sort: ConversationSort,
        bundle: MetadataBundle,
    ): List<Conversation> {
        val metaByThread = bundle.meta.associateBy { it.threadId }
        val draftsByThread = bundle.drafts.associateBy { it.threadId }
        val starredThreadIds = bundle.starred.map { it.threadId }.toSet()

        if (contacts.identityIndex.value.isEmpty() && contacts.hasPermission()) {
            contacts.refresh()
        }

        val threads = provider.loadThreads()
        if (threads.isEmpty()) return emptyList()

        val canonicalIds = threads.flatMap { it.recipientIds }.toSet()
        val canonicalMap = provider.loadCanonicalAddresses(canonicalIds)

        val visibleThreads = threads.filter { thread ->
            val meta = metaByThread[thread.threadId]
            val archived = thread.archived || meta?.isArchived == true
            val blocked = meta?.isBlocked == true
            when (filter) {
                InboxFilter.ARCHIVED -> archived && !blocked
                InboxFilter.ALL -> !archived && !blocked
                InboxFilter.UNREAD, InboxFilter.STARRED, InboxFilter.UNKNOWN -> !archived && !blocked
            }
        }
        if (visibleThreads.isEmpty()) return emptyList()

        val addressesByThread = visibleThreads.associate { thread ->
            val resolved = thread.recipientIds.mapNotNull { canonicalMap[it] }
                .filter { it.isNotBlank() }
                .distinctBy { PhoneNumbers.comparisonKey(it) }
            thread.threadId to resolved
        }
        val allAddresses = addressesByThread.values.flatten()
        val identities = contacts.identitiesFor(allAddresses)
        val facts = provider.lastMessageFacts(visibleThreads.map { it.threadId }.toSet())
        val rules = if (bundle.settings.categoriesEnabled) loadRules() else emptyList()
        val region = subscriptions.regionHint()

        val conversations = visibleThreads.mapNotNull { thread ->
            val meta = metaByThread[thread.threadId]
            val addresses = addressesByThread[thread.threadId].orEmpty()
            val primary = addresses.firstOrNull()
                ?: canonicalMap[thread.recipientIds.firstOrNull().orEmpty()].orEmpty()
            if (primary.isBlank() && thread.snippet.isNullOrBlank()) {
                // A thread we cannot resolve is not useful in the list; keep it out rather
                // than showing an empty "unknown" row.
                return@mapNotNull null
            }
            val identity = identities[primary]
                ?: contacts.lookup(primary)
            val displayName = displayNameFor(addresses, identity?.name, primary, region)
            val draft = draftsByThread[thread.threadId]
            val lastFacts = facts[thread.threadId]
            val isKnown = !identity?.name.isNullOrBlank() || identity?.contactId != null
            val classification = if (bundle.settings.categoriesEnabled) {
                SmartOrganizer.classify(thread.snippet, primary, rules, isKnown)
            } else {
                SmartOrganizer.Classification(emptySet(), null, false)
            }
            val override = ConversationCategory.fromStorage(meta?.categoryOverride)
            val categories = buildSet {
                addAll(classification.categories)
                if (override != null) {
                    remove(ConversationCategory.UNKNOWN)
                    add(override)
                }
            }
            Conversation(
                threadId = thread.threadId,
                recipientIds = thread.recipientIds,
                displayName = displayName,
                address = primary,
                snippet = thread.snippet?.takeIf { it.isNotBlank() } ?: "",
                lastTimestamp = thread.date,
                unreadCount = thread.unreadCount,
                hasDraft = !draft?.text.isNullOrBlank(),
                draftText = draft?.text,
                isPinned = meta?.isPinned == true,
                isArchived = thread.archived || meta?.isArchived == true,
                isMuted = meta?.isMuted == true,
                isStarred = meta?.isStarred == true || thread.threadId in starredThreadIds,
                isGroup = addresses.size > 1,
                isUnknownSender = !isKnown,
                lastMessageStatus = mapStatus(lastFacts),
                lastDirection = when (lastFacts?.type) {
                    android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX -> Direction.INCOMING
                    null -> Direction.INCOMING
                    else -> Direction.OUTGOING
                },
                lastSource = if (lastFacts?.isMms == true) MessageSource.MMS else MessageSource.SMS,
                subscriptionId = lastFacts?.subscriptionId ?: -1,
                photoUri = identity?.photoUri,
                categories = categories,
                hasAttachmentsInLastMessage = thread.hasAttachment,
                isBlocked = meta?.isBlocked == true,
                priority = meta?.priority == true,
            )
        }

        val filtered = conversations.filter { conversation ->
            when (filter) {
                InboxFilter.UNREAD -> conversation.unreadCount > 0
                InboxFilter.STARRED -> conversation.isStarred ||
                    conversation.categories.contains(ConversationCategory.VERIFICATION)
                InboxFilter.UNKNOWN -> conversation.isUnknownSender
                InboxFilter.ALL, InboxFilter.ARCHIVED -> true
            }
        }

        return filtered.sortedWith(
            when (sort) {
                ConversationSort.UNREAD_FIRST -> compareByDescending<Conversation> { it.isPinned }
                    .thenByDescending { it.isUnread }
                    .thenByDescending { it.lastTimestamp }
                ConversationSort.RECENT -> compareByDescending<Conversation> { it.isPinned }
                    .thenByDescending { it.lastTimestamp }
            }
        )
    }

    private fun mapStatus(facts: SystemSmsDataSource.LastMessageFacts?): MessageStatus {
        if (facts == null) return MessageStatus.UNKNOWN
        if (facts.isMms) return MessageStatus.SENT
        if (facts.type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX) {
            return MessageStatus.RECEIVED
        }
        return when (facts.status) {
            android.provider.Telephony.Sms.STATUS_PENDING -> MessageStatus.SENDING
            android.provider.Telephony.Sms.STATUS_FAILED -> MessageStatus.FAILED
            android.provider.Telephony.Sms.STATUS_COMPLETE -> MessageStatus.SENT
            else -> MessageStatus.SENT
        }
    }

    private fun displayNameFor(
        addresses: List<String>,
        contactName: String?,
        primaryAddress: String,
        regionHint: String?,
    ): String {
        if (addresses.size > 1) {
            val names = addresses.take(3).map { address ->
                contacts.identityIndex.value[PhoneNumbers.comparisonKey(address)]?.name
                    ?: PhoneNumbers.formatForDisplay(address, regionHint)
            }
            val remaining = addresses.size - names.size
            return if (remaining > 0) {
                names.joinToString(", ") + " +$remaining"
            } else {
                names.joinToString(", ")
            }
        }
        if (!contactName.isNullOrBlank()) return contactName
        return PhoneNumbers.formatForDisplay(primaryAddress, regionHint)
    }

    private suspend fun loadRules(): List<CategoryRule> = organization.rulesSnapshot()

    // ------------------------------------------------------------------------------------
    // Single conversation
    // ------------------------------------------------------------------------------------

    fun observeConversation(threadId: Long): Flow<Conversation?> =
        combine(changeStream, metadataFlow) { _, bundle -> bundle }
            .mapLatest { withContext(Dispatchers.IO) { getConversation(threadId) } }
            .flowOn(Dispatchers.IO)

    suspend fun getConversation(threadId: Long): Conversation? = withContext(Dispatchers.IO) {
        if (threadId <= 0) return@withContext null
        val meta = metaDao.get(threadId)
        val thread = provider.loadThreads().firstOrNull { it.threadId == threadId }
        val addresses = provider.threadRecipientAddresses(threadId)
        val primary = addresses.firstOrNull()
            ?: thread?.recipientIds?.firstOrNull()?.let { provider.loadCanonicalAddresses(setOf(it))[it] }
            ?: ""
        val identity = if (primary.isNotBlank() && primary != "insert-address-token") {
            contacts.lookup(primary)
        } else {
            null
        }
        val draft = draftDao.get(threadId)
        Conversation(
            threadId = threadId,
            recipientIds = thread?.recipientIds ?: emptyList(),
            displayName = identity?.name?.takeIf { !it.isNullOrBlank() }
                ?: PhoneNumbers.formatForDisplay(primary, subscriptions.regionHint()),
            address = primary,
            snippet = thread?.snippet.orEmpty(),
            lastTimestamp = thread?.date ?: System.currentTimeMillis(),
            unreadCount = thread?.unreadCount ?: 0,
            hasDraft = !draft?.text.isNullOrBlank(),
            draftText = draft?.text,
            isPinned = meta?.isPinned == true,
            isArchived = thread?.archived == true || meta?.isArchived == true,
            isMuted = meta?.isMuted == true,
            isStarred = meta?.isStarred == true,
            isGroup = (thread?.recipientIds?.size ?: 0) > 1,
            isUnknownSender = identity?.contactId == null,
            lastMessageStatus = MessageStatus.UNKNOWN,
            lastDirection = Direction.INCOMING,
            lastSource = MessageSource.SMS,
            subscriptionId = provider.lastMessageFacts(setOf(threadId))[threadId]?.subscriptionId ?: -1,
            photoUri = identity?.photoUri,
            categories = emptySet(),
            hasAttachmentsInLastMessage = false,
            isBlocked = meta?.isBlocked == true,
            priority = meta?.priority == true,
        )
    }

    /**
     * Messages for the conversation screen, newest page first (then sorted ascending for
     * display). Pending outbox rows are merged so a send is visible immediately even when
     * the system database is not ours to write.
     */
    fun observeMessages(threadId: Long, pageSize: Int = 50): Flow<MessagePage> =
        combine(changeStream, metadataFlow) { _, bundle -> bundle }
            .mapLatest { bundle ->
                withContext(Dispatchers.IO) {
                    loadMessagePage(threadId, pageSize, before = null, bundle)
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    suspend fun loadOlderMessages(
        threadId: Long,
        beforeTimestamp: Long,
        pageSize: Int = 50,
    ): MessagePage = withContext(Dispatchers.IO) {
        loadMessagePage(threadId, pageSize, beforeTimestamp, currentBundle())
    }

    private suspend fun currentBundle(): MetadataBundle = MetadataBundle(
        meta = metaDao.snapshot(),
        drafts = draftDao.snapshot(),
        starred = starredDao.snapshot(),
        settings = settingsRepository.settings.first(),
    )

    private suspend fun loadMessagePage(
        threadId: Long,
        pageSize: Int,
        before: Long?,
        bundle: MetadataBundle,
    ): MessagePage {
        val messages = provider.loadMessages(threadId, pageSize + 1, before).toMutableList()
        val hasMore = messages.size > pageSize
        val trimmed = if (hasMore) messages.takeLast(pageSize) else messages
        val outbox = outboxDao.pendingForThread(threadId)
            .filter { it.state == OutboxState.QUEUED.name || it.state == OutboxState.FAILED.name }
        val synthetic = outbox.filter { it.systemMessageId == null }.map { it.toSyntheticMessage() }
        val starredIds = starredDao.snapshot().filter { it.threadId == threadId }.map { it.messageId }.toSet()
        val settings = bundle.settings
        val enriched = (trimmed + synthetic)
            .sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
            .map { message ->
                message.copy(
                    starred = message.id in starredIds,
                    body = if (message.source == MessageSource.MMS && message.body.isBlank()) {
                        message.body
                    } else {
                        message.body
                    },
                    hasOtp = settings.otpDetectionEnabled &&
                        message.direction == Direction.INCOMING &&
                        SmartOrganizer.findOtp(message.body, message.id, message.threadId) != null,
                )
            }
        return MessagePage(enriched, hasMore)
    }

    private fun OutboxEntry.toSyntheticMessage(): Message = Message(
        id = -id,
        source = MessageSource.SMS,
        threadId = threadId,
        address = address,
        body = body,
        timestamp = createdAt,
        direction = Direction.OUTGOING,
        status = when (state) {
            OutboxState.FAILED.name -> MessageStatus.FAILED
            OutboxState.DELIVERED.name -> MessageStatus.DELIVERED
            OutboxState.SENT.name -> MessageStatus.SENT
            else -> MessageStatus.SENDING
        },
        read = true,
        starred = false,
        subscriptionId = subscriptionId,
    )

    fun observeDraft(threadId: Long): Flow<String> =
        draftDao.observe(threadId).map { it?.text.orEmpty() }.flowOn(Dispatchers.IO)

    fun observeDraftEntity(threadId: Long): Flow<DraftEntity?> =
        draftDao.observe(threadId).flowOn(Dispatchers.IO)

    suspend fun saveDraft(threadId: Long, text: String) = withContext(Dispatchers.IO) {
        if (threadId <= 0) return@withContext
        if (text.isBlank()) {
            draftDao.delete(threadId)
        } else {
            draftDao.upsert(
                DraftEntity(threadId = threadId, text = text, updatedAt = System.currentTimeMillis())
            )
        }
    }

    suspend fun clearDraft(threadId: Long) = withContext(Dispatchers.IO) { draftDao.delete(threadId) }

    // ------------------------------------------------------------------------------------
    // Metadata mutations
    // ------------------------------------------------------------------------------------

    private suspend fun mutateMeta(
        threadId: Long,
        transform: (ConversationMetaEntity) -> ConversationMetaEntity,
    ) {
        if (threadId <= 0) return
        val existing = metaDao.get(threadId) ?: ConversationMetaEntity(threadId = threadId)
        val updated = transform(existing).copy(updatedAt = System.currentTimeMillis())
        metaDao.upsert(updated)
        notifier.notifyChanged()
    }

    suspend fun setPinned(threadId: Long, pinned: Boolean) {
        mutateMeta(threadId) { it.copy(isPinned = pinned) }
    }

    suspend fun setStarredConversation(threadId: Long, starred: Boolean) {
        mutateMeta(threadId) { it.copy(isStarred = starred) }
    }

    suspend fun setMuted(threadId: Long, muted: Boolean) {
        mutateMeta(threadId) { it.copy(isMuted = muted) }
    }

    suspend fun setPriority(threadId: Long, priority: Boolean) {
        mutateMeta(threadId) { it.copy(priority = priority) }
    }

    suspend fun setBlocked(threadId: Long, blocked: Boolean) {
        mutateMeta(threadId) { it.copy(isBlocked = blocked) }
    }

    suspend fun setCustomSound(threadId: Long, sound: String?) {
        mutateMeta(threadId) { it.copy(customSound = sound) }
    }

    suspend fun setCategoryOverride(threadId: Long, category: ConversationCategory?) {
        mutateMeta(threadId) { it.copy(categoryOverride = category?.name) }
    }

    /** Archives in our metadata and in the system thread table when allowed. */
    suspend fun setArchived(threadId: Long, archived: Boolean) {
        mutateMeta(threadId) { it.copy(isArchived = archived) }
        provider.setThreadArchived(threadId, archived)
        notifier.notifyChanged()
    }

    suspend fun markRead(threadId: Long) {
        provider.setThreadRead(threadId, true)
        notifier.notifyChanged()
    }

    /** Marks every unread conversation read. Used by the notification summary action. */
    suspend fun markAllRead(threadIds: List<Long>) {
        threadIds.forEach { threadId ->
            runCatching { provider.setThreadRead(threadId, true) }
        }
        notifier.notifyChanged()
    }

    suspend fun unreadThreadIds(): List<Long> = withContext(Dispatchers.IO) {
        provider.loadThreads().filter { it.unreadCount > 0 }.map { it.threadId }
    }

    suspend fun setUnreadCountForThread(threadId: Long): Int = withContext(Dispatchers.IO) {
        provider.loadThreads().firstOrNull { it.threadId == threadId }?.unreadCount ?: 0
    }

    suspend fun markUnread(threadId: Long) {
        // Android has no "mark unread" primitive; flipping read=0 on the newest inbound
        // message restores the unread state without touching the rest of the thread.
        val messages = provider.loadMessages(threadId, 20)
        val newestIncoming = messages.lastOrNull { it.direction == Direction.INCOMING }
        if (newestIncoming != null && newestIncoming.source == MessageSource.SMS) {
            provider.setMessageRead(newestIncoming.id, false)
        } else {
            provider.setThreadRead(threadId, false)
        }
        notifier.notifyChanged()
    }

    suspend fun deleteThread(threadId: Long) {
        provider.deleteThread(threadId)
        metaDao.delete(threadId)
        draftDao.delete(threadId)
        starredDao.deleteForThread(threadId)
        notifier.notifyChanged()
    }

    suspend fun deleteMessage(message: Message): Boolean {
        val deleted = provider.deleteMessage(message.id, message.source)
        if (deleted) {
            starredDao.delete(message.id)
            notifier.notifyChanged()
        }
        return deleted
    }

    suspend fun toggleStar(message: Message): Boolean = withContext(Dispatchers.IO) {
        val starred = starredDao.isStarred(message.id)
        if (starred) {
            starredDao.delete(message.id)
        } else {
            starredDao.upsert(
                StarredMessageEntity(
                    messageId = message.id,
                    threadId = message.threadId,
                    source = message.source.name,
                )
            )
        }
        notifier.notifyChanged()
        !starred
    }

    suspend fun starredMessages(): List<Message> = withContext(Dispatchers.IO) {
        starredDao.snapshot().mapNotNull { entry ->
            provider.loadMessage(entry.messageId, MessageSource.valueOf(entry.source))
        }
    }

    suspend fun messageById(id: Long, source: MessageSource): Message? =
        provider.loadMessage(id, source)

    fun observeBlockedThreads(): Flow<List<Conversation>> =
        combine(changeStream, metadataFlow) { _, bundle -> bundle }
            .mapLatest { bundle ->
                withContext(Dispatchers.IO) {
                    buildInbox(InboxFilter.ARCHIVED, ConversationSort.RECENT, bundle)
                        .filter { it.isBlocked }
                }
            }
            .flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------------------------

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext SearchResults(emptyList(), emptyList())
        val messages = provider.searchMessages(trimmed)
        val hits = messages.mapNotNull { message ->
            val matchStart = message.body.indexOf(trimmed, ignoreCase = true)
            if (matchStart < 0) return@mapNotNull null
            val identity = contacts.identitiesFor(listOf(message.address))[message.address]
            SearchHit(
                threadId = message.threadId,
                messageId = message.id,
                displayName = identity?.name
                    ?: PhoneNumbers.formatForDisplay(message.address, subscriptions.regionHint()),
                address = message.address,
                snippet = message.body,
                timestamp = message.timestamp,
                matchStart = matchStart,
                matchEnd = matchStart + trimmed.length,
            )
        }
        val conversations = buildInbox(InboxFilter.ALL, ConversationSort.RECENT, currentBundle())
            .filter {
                it.displayName.contains(trimmed, ignoreCase = true) ||
                    it.address.contains(trimmed) ||
                    PhoneNumbers.digitsOnly(it.address).contains(PhoneNumbers.digitsOnly(trimmed))
            }
        SearchResults(hits, conversations)
    }

    private suspend fun metadataFlowSnapshot(): MetadataBundle = currentBundle()

    // ------------------------------------------------------------------------------------
    // Thread resolution
    // ------------------------------------------------------------------------------------

    /** Existing system thread for an address, if any. Read-only and safe for any role. */
    suspend fun findThreadForAddress(address: String): Long? = withContext(Dispatchers.IO) {
        provider.findExistingThreadId(address)
    }

    /**
     * Thread id to open for a new message. Falls back to a negative placeholder so the
     * composer can open before the system thread exists (created on first send).
     */
    suspend fun resolveThreadId(address: String): Long = withContext(Dispatchers.IO) {
        provider.findExistingThreadId(address)
            ?: (-kotlin.math.abs(PhoneNumbers.comparisonKey(address).hashCode().toLong()) - 1L)
    }

    suspend fun ensureThread(address: String): Long? = withContext(Dispatchers.IO) {
        provider.obtainThreadId(address)
    }

    /** Date-range search used by "yesterday"/"this week" style queries. */
    suspend fun searchByDateRange(startMillis: Long, endMillis: Long): List<SearchHit> =
        withContext(Dispatchers.IO) {
            provider.loadMessagesBetween(startMillis, endMillis).mapNotNull { message ->
                val identity = contacts.identitiesFor(listOf(message.address))[message.address]
                SearchHit(
                    threadId = message.threadId,
                    messageId = message.id,
                    displayName = identity?.name
                        ?: PhoneNumbers.formatForDisplay(message.address, subscriptions.regionHint()),
                    address = message.address,
                    snippet = message.body,
                    timestamp = message.timestamp,
                    matchStart = 0,
                    matchEnd = 0,
                )
            }
        }

    suspend fun rulesForClassification(): List<CategoryRule> = loadRules()

    suspend fun refreshContacts() {
        runCatching { contacts.refresh() }
            .onFailure { Logx.w("Contacts", "refresh failed", it) }
    }
}
