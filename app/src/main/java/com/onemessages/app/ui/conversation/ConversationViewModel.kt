package com.onemessages.app.ui.conversation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.contacts.ContactsRepository
import com.onemessages.app.core.Logx
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.repository.AttachmentRepository
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.data.repository.MessagePage
import com.onemessages.app.domain.SmartOrganizer
import com.onemessages.app.model.Attachment
import com.onemessages.app.model.ContactIdentity
import com.onemessages.app.model.Conversation
import com.onemessages.app.model.Message
import com.onemessages.app.model.OtpMatch
import com.onemessages.app.model.SendFailureReason
import com.onemessages.app.model.SendOutcome
import com.onemessages.app.model.SimInfo
import com.onemessages.app.model.MessageStatus
import com.onemessages.app.notifications.MessageNotifier
import com.onemessages.app.telephony.SmsSender
import com.onemessages.app.telephony.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationUiState(
    val threadId: Long = -1,
    val address: String = "",
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val loading: Boolean = true,
    val error: Boolean = false,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val draft: String = "",
    val sending: Boolean = false,
    val sims: List<SimInfo> = emptyList(),
    val selectedSimId: Int = -1,
    val contact: ContactIdentity? = null,
    val blocked: Boolean = false,
    val otpVisible: Boolean = false,
    val canSendMms: Boolean = false,
) {
    val isNewConversation: Boolean get() = threadId <= 0
    val displayTitle: String
        get() = conversation?.displayName?.takeIf { it.isNotBlank() }
            ?: contact?.name?.takeIf { it.isNotBlank() }
            ?: address
    val canSend: Boolean get() = !sending && draft.isNotBlank() && sims.isNotEmpty()
}

sealed interface ConversationEvent {
    data class ShowSnackbar(val resId: Int, val arg: String? = null) : ConversationEvent
    data class SendFailed(val reason: SendFailureReason) : ConversationEvent
    data class StartedConversation(val threadId: Long) : ConversationEvent
    data object MessageDeleted : ConversationEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val smsSender: SmsSender,
    private val settingsRepository: SettingsRepository,
    private val subscriptions: SubscriptionRepository,
    private val contactsRepository: ContactsRepository,
    private val attachments: AttachmentRepository,
    private val notifier: MessageNotifier,
) : ViewModel() {

    // Navigation passes threadId as a Long; older links may still hand over a string, so both
    // are accepted rather than crashing on a class cast.
    private val initialThreadId: Long = savedStateHandle.get<Long>("threadId")
        ?: savedStateHandle.get<String>("threadId")?.toLongOrNull()
        ?: -1L
    private val initialAddress: String = savedStateHandle.get<String>("address").orEmpty()

    private val _state = MutableStateFlow(
        ConversationUiState(threadId = initialThreadId, address = initialAddress, loading = true)
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<ConversationEvent?>(null)
    val events: StateFlow<ConversationEvent?> = _events.asStateFlow()

    private var sendJob: Job? = null
    private var draftJob: Job? = null
    private var currentThreadId: Long = initialThreadId
    private val draftInput = MutableStateFlow("")
    private var draftInitialised = false
    private var oldestLoadedTimestamp: Long = Long.MAX_VALUE

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings
            settings.collectLatest { current ->
                val sims = subscriptions.sims()
                val selected = when {
                    _state.value.selectedSimId in sims.map { it.subscriptionId } -> _state.value.selectedSimId
                    current.preferredSubscriptionId in sims.map { it.subscriptionId } -> current.preferredSubscriptionId
                    sims.isNotEmpty() -> sims.first().subscriptionId
                    else -> -1
                }
                _state.update {
                    it.copy(
                        sims = sims,
                        selectedSimId = selected,
                        canSendMms = subscriptions.isMobileDataEnabled() && sims.isNotEmpty(),
                    )
                }
            }
        }
        viewModelScope.launch { observeConversation() }
        viewModelScope.launch { observeMessages() }
        viewModelScope.launch { persistDraft() }
        if (initialThreadId > 0) {
            viewModelScope.launch {
                val current = settingsRepository.settings.first()
                if (current.markReadOnOpen) {
                    conversationRepository.markRead(initialThreadId)
                    notifier.cancelForThread(initialThreadId)
                }
            }
        } else {
            _state.update { it.copy(loading = false) }
            resolveContact()
        }
    }

    private suspend fun observeConversation() {
        if (initialThreadId <= 0) return
        conversationRepository.observeConversation(initialThreadId).collect { conversation ->
            _state.update {
                it.copy(
                    conversation = conversation,
                    blocked = conversation?.isBlocked == true,
                    address = it.address.ifBlank { conversation?.address.orEmpty() },
                )
            }
            if (conversation != null && _state.value.contact == null) {
                resolveContact()
            }
        }
    }

    private suspend fun observeMessages() {
        if (initialThreadId <= 0) return
        conversationRepository.observeMessages(initialThreadId).collect { page ->
            applyPage(page, appendOlder = false)
        }
    }

    private fun applyPage(page: MessagePage, appendOlder: Boolean) {
        val merged = if (appendOlder) {
            (page.messages + _state.value.messages)
                .distinctBy { message -> message.id }
                .sortedBy { message -> message.timestamp }
        } else {
            page.messages
        }
        if (merged.isNotEmpty()) {
            oldestLoadedTimestamp = merged.firstOrNull()?.timestamp ?: oldestLoadedTimestamp
        }
        _state.update {
            it.copy(
                messages = merged,
                loading = false,
                hasMore = page.hasMore,
                loadingMore = false,
            )
        }
        // A draft may have been written for this thread earlier in the session.
        if (!draftInitialised) loadDraft()
    }

    private suspend fun loadDraft() {
        if (initialThreadId <= 0) {
            draftInitialised = true
            return
        }
        val entity = conversationRepository.observeDraftEntity(initialThreadId).first()
        if (entity != null && entity.text.isNotBlank() && _state.value.draft.isBlank()) {
            draftInput.value = entity.text
            _state.update { it.copy(draft = entity.text) }
            _events.value = ConversationEvent.ShowSnackbar(com.onemessages.app.R.string.draft_restored)
        }
        draftInitialised = true
    }

    private suspend fun persistDraft() {
        draftInput
            .drop(1)
            .debounce(450)
            .distinctUntilChanged()
            .collect { text ->
                val threadId = if (currentThreadId > 0) {
                    currentThreadId
                } else {
                    initialAddress.takeIf { it.isNotBlank() }?.let { address ->
                        conversationRepository.findThreadForAddress(address)
                    } ?: -1L
                }
                if (threadId > 0 && draftInitialised) {
                    currentThreadId = threadId
                    conversationRepository.saveDraft(threadId, text)
                }
            }
    }

    private fun resolveContact() {
        viewModelScope.launch {
            val address = _state.value.address.ifBlank { _state.value.conversation?.address.orEmpty() }
            if (address.isBlank()) return@launch
            val identity = contactsRepository.lookup(address)
            _state.update { it.copy(contact = identity) }
            if (_state.value.threadId <= 0) {
                val threadId = conversationRepository.findThreadForAddress(address)
                if (threadId != null && threadId > 0) {
                    currentThreadId = threadId
                    _state.update { it.copy(threadId = threadId) }
                    launch { observeConversation() }
                    launch { observeMessages() }
                    loadDraft()
                }
            }
        }
    }

    fun onDraftChanged(text: String) {
        _state.update { it.copy(draft = text) }
        draftInput.value = text
    }

    fun selectSim(subscriptionId: Int) {
        _state.update { it.copy(selectedSimId = subscriptionId) }
    }

    fun send() {
        val current = _state.value
        if (current.sending) return
        if (current.draft.isBlank()) return
        if (current.sims.isEmpty()) {
            _events.value = ConversationEvent.SendFailed(SendFailureReason.NO_SIM)
            return
        }
        val body = current.draft
        _state.update { it.copy(sending = true) }
        sendJob = viewModelScope.launch {
            val outcome = smsSender.sendSms(
                address = current.address.ifBlank { current.conversation?.address.orEmpty() },
                body = body,
                threadId = current.threadId,
                subscriptionId = current.selectedSimId,
            )
            when (outcome) {
                is SendOutcome.Accepted -> {
                    _state.update { it.copy(sending = false, draft = "") }
                    draftInput.value = ""
                    if (current.threadId > 0) {
                        conversationRepository.clearDraft(current.threadId)
                    }
                    if (current.threadId <= 0) {
                        val resolved = conversationRepository.findThreadForAddress(
                            current.address.ifBlank { current.conversation?.address.orEmpty() }
                        )
                        if (resolved != null && resolved > 0) {
                            currentThreadId = resolved
                            _state.update { it.copy(threadId = resolved) }
                            _events.value = ConversationEvent.StartedConversation(resolved)
                        }
                    }
                }
                is SendOutcome.Rejected -> {
                    _state.update { it.copy(sending = false) }
                    _events.value = ConversationEvent.SendFailed(outcome.reason)
                    Logx.w("Compose", "send rejected: ${outcome.reason.name}", null)
                }
            }
        }
    }

    fun retryMessage(message: Message) {
        viewModelScope.launch {
            if (message.id < 0) {
                smsSender.retry(-message.id)
            } else if (message.status == MessageStatus.FAILED) {
                smsSender.sendSms(
                    address = message.address.ifBlank { _state.value.address },
                    body = message.body,
                    threadId = message.threadId,
                    subscriptionId = message.subscriptionId,
                    isRetry = true,
                )
            }
        }
    }

    fun loadOlder() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = conversationRepository.loadOlderMessages(
                threadId = current.threadId,
                beforeTimestamp = oldestLoadedTimestamp,
            )
            applyPage(page, appendOlder = true)
            if (page.messages.isEmpty()) _state.update { it.copy(loadingMore = false, hasMore = false) }
        }
    }

    fun toggleStar(message: Message) {
        viewModelScope.launch {
            val nowStarred = conversationRepository.toggleStar(message)
            _events.value = ConversationEvent.ShowSnackbar(
                if (nowStarred) com.onemessages.app.R.string.cd_starred
                else com.onemessages.app.R.string.unstar
            )
        }
    }

    fun deleteMessage(message: Message) {
        viewModelScope.launch {
            val deleted = conversationRepository.deleteMessage(message)
            if (deleted) _events.value = ConversationEvent.MessageDeleted
        }
    }

    fun markUnread() {
        viewModelScope.launch {
            if (_state.value.threadId > 0) {
                conversationRepository.markUnread(_state.value.threadId)
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val conversation = _state.value.conversation ?: return@launch
            conversationRepository.setMuted(conversation.threadId, !conversation.isMuted)
            _events.value = ConversationEvent.ShowSnackbar(
                if (conversation.isMuted) com.onemessages.app.R.string.conversation_unmuted
                else com.onemessages.app.R.string.conversation_muted
            )
        }
    }

    fun toggleBlock() {
        viewModelScope.launch {
            val conversation = _state.value.conversation ?: return@launch
            conversationRepository.setBlocked(conversation.threadId, !conversation.isBlocked)
            if (!conversation.isBlocked) notifier.cancelForThread(conversation.threadId)
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val conversation = _state.value.conversation ?: return@launch
            conversationRepository.setPinned(conversation.threadId, !conversation.isPinned)
        }
    }

    fun deleteConversation(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val threadId = _state.value.threadId
            if (threadId > 0) {
                conversationRepository.deleteThread(threadId)
                notifier.cancelForThread(threadId)
            }
            onDeleted()
        }
    }

    fun archiveConversation() {
        viewModelScope.launch {
            val conversation = _state.value.conversation ?: return@launch
            conversationRepository.setArchived(conversation.threadId, !conversation.isArchived)
        }
    }

    fun otpFor(message: Message): OtpMatch? {
        if (message.direction != com.onemessages.app.model.Direction.INCOMING) return null
        return SmartOrganizer.findOtp(message.body, message.id, message.threadId)
    }

    fun toggleOtpVisible() {
        _state.update { it.copy(otpVisible = !it.otpVisible) }
    }

    suspend fun saveAttachment(attachment: Attachment, destination: Uri): Boolean =
        attachments.saveToDocument(Uri.parse(attachment.uriString), destination)

    fun suggestedFileName(attachment: Attachment): String =
        attachments.sanitizeFileName(attachment.fileName ?: "attachment", attachment.contentType)

    fun shareText(message: Message): String = message.body

    fun clearEvent() {
        _events.value = null
    }
}
