package com.onemessages.app.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.model.Conversation
import com.onemessages.app.model.ConversationSort
import com.onemessages.app.model.InboxFilter
import com.onemessages.app.notifications.MessageNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val conversations: List<Conversation> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val filter: InboxFilter = InboxFilter.ALL,
    val selection: Set<Long> = emptySet(),
    val totalUnread: Int = 0,
    val sort: ConversationSort = ConversationSort.RECENT,
) {
    val isSelecting: Boolean get() = selection.isNotEmpty()
}

sealed interface InboxEvent {
    data class Deleted(val count: Int) : InboxEvent
    data class Info(val resId: Int, val count: Int = 0) : InboxEvent
}

private data class FeedState(
    val filter: InboxFilter,
    val conversations: List<Conversation>,
    val loading: Boolean = false,
    val error: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val notifier: MessageNotifier,
) : ViewModel() {

    private val initialFilter = when (savedStateHandle.get<String>("filter")) {
        "archived" -> InboxFilter.ARCHIVED
        "starred" -> InboxFilter.STARRED
        "unread" -> InboxFilter.UNREAD
        "unknown" -> InboxFilter.UNKNOWN
        else -> InboxFilter.ALL
    }

    private val _filter = MutableStateFlow(initialFilter)
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    private val _sort = MutableStateFlow(ConversationSort.RECENT)
    private val _events = MutableStateFlow<InboxEvent?>(null)
    val events: StateFlow<InboxEvent?> = _events.asStateFlow()

    private val feed: Flow<FeedState> = combine(_filter, _sort) { filter, sort -> filter to sort }
        .flatMapLatest { (filter, sort) ->
            conversationRepository.observeInbox(filter, sort)
                .map { conversations -> FeedState(filter, conversations) }
                .onStart { emit(FeedState(filter, emptyList(), loading = true)) }
                .catch { throwable ->
                    com.onemessages.app.core.Logx.w("Inbox", "inbox load failed", throwable)
                    emit(FeedState(filter, emptyList(), error = true))
                }
        }

    val uiState: StateFlow<InboxUiState> = combine(feed, _selection, _sort) { feedState, selection, sort ->
        InboxUiState(
            conversations = feedState.conversations,
            loading = feedState.loading,
            error = feedState.error,
            filter = feedState.filter,
            selection = selection,
            totalUnread = feedState.conversations.sumOf { it.unreadCount },
            sort = sort,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        InboxUiState(loading = true, filter = initialFilter),
    )

    fun setFilter(filter: InboxFilter) {
        _filter.value = filter
        _selection.value = emptySet()
    }

    fun setSort(sort: ConversationSort) {
        _sort.value = sort
    }

    fun toggleSelection(threadId: Long) {
        _selection.value = _selection.value.let { current ->
            if (threadId in current) current - threadId else current + threadId
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun togglePinned(conversation: Conversation) = viewModelScope.launch {
        conversationRepository.setPinned(conversation.threadId, !conversation.isPinned)
    }

    fun toggleMuted(conversation: Conversation) = viewModelScope.launch {
        conversationRepository.setMuted(conversation.threadId, !conversation.isMuted)
    }

    fun toggleStarred(conversation: Conversation) = viewModelScope.launch {
        conversationRepository.setStarredConversation(conversation.threadId, !conversation.isStarred)
    }

    fun setArchived(conversation: Conversation, archived: Boolean) = viewModelScope.launch {
        conversationRepository.setArchived(conversation.threadId, archived)
    }

    fun setBlocked(conversation: Conversation, blocked: Boolean) = viewModelScope.launch {
        conversationRepository.setBlocked(conversation.threadId, blocked)
        notifier.cancelForThread(conversation.threadId)
        _events.value = InboxEvent.Info(
            if (blocked) com.onemessages.app.R.string.per_conversation_blocked
            else com.onemessages.app.R.string.per_conversation_unblock
        )
    }

    fun toggleRead(conversation: Conversation) = viewModelScope.launch {
        if (conversation.isUnread) {
            conversationRepository.markRead(conversation.threadId)
            notifier.cancelForThread(conversation.threadId)
        } else {
            conversationRepository.markUnread(conversation.threadId)
        }
    }

    fun markSelectedRead(read: Boolean) = viewModelScope.launch {
        _selection.value.forEach { threadId ->
            if (read) {
                conversationRepository.markRead(threadId)
            } else {
                conversationRepository.markUnread(threadId)
            }
            notifier.cancelForThread(threadId)
        }
        _selection.value = emptySet()
    }

    fun archiveSelected(archived: Boolean) = viewModelScope.launch {
        val ids = _selection.value
        ids.forEach { threadId -> conversationRepository.setArchived(threadId, archived) }
        _selection.value = emptySet()
        _events.value = InboxEvent.Info(com.onemessages.app.R.string.inbox_selected_count, ids.size)
    }

    fun muteSelected(muted: Boolean) = viewModelScope.launch {
        val ids = _selection.value
        ids.forEach { threadId -> conversationRepository.setMuted(threadId, muted) }
        _selection.value = emptySet()
        _events.value = InboxEvent.Info(com.onemessages.app.R.string.inbox_selected_count, ids.size)
    }

    fun deleteSelected() = viewModelScope.launch {
        val ids = _selection.value
        ids.forEach { threadId ->
            conversationRepository.deleteThread(threadId)
            notifier.cancelForThread(threadId)
        }
        _selection.value = emptySet()
        if (ids.isNotEmpty()) _events.value = InboxEvent.Deleted(ids.size)
    }

    fun deleteConversation(conversation: Conversation) = viewModelScope.launch {
        conversationRepository.deleteThread(conversation.threadId)
        notifier.cancelForThread(conversation.threadId)
        _events.value = InboxEvent.Deleted(1)
    }

    fun clearEvent() {
        _events.value = null
    }

    fun refresh() {
        viewModelScope.launch { conversationRepository.refreshContacts() }
    }
}
