package com.onemessages.app.ui.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.contacts.ContactsRepository
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.model.ContactIdentity
import com.onemessages.app.model.DeviceContact
import com.onemessages.app.model.SendFailureReason
import com.onemessages.app.model.SendOutcome
import com.onemessages.app.model.SimInfo
import com.onemessages.app.telephony.SmsSender
import com.onemessages.app.telephony.SubscriptionRepository
import com.onemessages.app.util.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Recipient(val address: String, val displayName: String?, val photoUri: String? = null)

data class NewMessageUiState(
    val recipients: List<Recipient> = emptyList(),
    val query: String = "",
    val body: String = "",
    val sending: Boolean = false,
    val sims: List<SimInfo> = emptyList(),
    val selectedSimId: Int = -1,
    val suggestions: List<DeviceContact> = emptyList(),
    val errorRes: Int? = null,
    val mmsBlocked: Boolean = true,
)

sealed interface NewMessageEvent {
    data class OpenConversation(val threadId: Long, val address: String) : NewMessageEvent
    data class ShowMessage(val resId: Int) : NewMessageEvent
    data object Sent : NewMessageEvent
}

@HiltViewModel
class NewMessageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val conversationRepository: ConversationRepository,
    private val smsSender: SmsSender,
    private val subscriptions: SubscriptionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val prefillAddress = savedStateHandle.get<String>("address").orEmpty()
    private val prefillBody = savedStateHandle.get<String>("body").orEmpty()

    private val _state = MutableStateFlow(
        NewMessageUiState(
            recipients = if (prefillAddress.isBlank()) {
                emptyList()
            } else {
                listOf(Recipient(prefillAddress, null))
            },
            body = prefillBody,
        )
    )
    val state: StateFlow<NewMessageUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<NewMessageEvent?>(null)
    val events: StateFlow<NewMessageEvent?> = _events.asStateFlow()

    init {
        viewModelScope.launch {
            contactsRepository.refresh()
            val settings = settingsRepository.settings.first()
            val sims = subscriptions.sims()
            val preferred = when {
                settings.preferredSubscriptionId in sims.map { it.subscriptionId } ->
                    settings.preferredSubscriptionId
                sims.isNotEmpty() -> sims.first().subscriptionId
                else -> -1
            }
            _state.update { it.copy(sims = sims, selectedSimId = preferred) }
        }
        viewModelScope.launch {
            contactsRepository.contacts.collect { contacts ->
                _state.update { current ->
                    current.copy(suggestions = filterSuggestions(contacts, current.query))
                }
            }
        }
        if (prefillAddress.isNotBlank()) {
            viewModelScope.launch {
                val identity = contactsRepository.lookup(prefillAddress)
                addRecipient(Recipient(prefillAddress, identity.name, identity.photoUri))
            }
        }
    }

    fun onQueryChanged(query: String) {
        _state.update { current ->
            current.copy(query = query, suggestions = filterSuggestions(contactsRepository.contacts.value, query))
        }
    }

    fun addTypedRecipient() {
        val raw = _state.value.query.trim()
        if (!PhoneNumbers.looksValid(raw)) {
            _state.update { it.copy(errorRes = com.onemessages.app.R.string.invalid_number) }
            return
        }
        viewModelScope.launch {
            val identity: ContactIdentity = contactsRepository.lookup(raw)
            addRecipient(Recipient(raw, identity.name, identity.photoUri))
        }
    }

    fun addRecipient(recipient: Recipient) {
        _state.update { current ->
            if (current.recipients.any { PhoneNumbers.sameNumber(it.address, recipient.address) }) {
                current.copy(query = "")
            } else {
                current.copy(recipients = current.recipients + recipient, query = "", errorRes = null)
            }
        }
    }

    fun removeRecipient(address: String) {
        _state.update { current ->
            current.copy(recipients = current.recipients.filterNot { it.address == address })
        }
    }

    fun selectSim(subscriptionId: Int) {
        _state.update { it.copy(selectedSimId = subscriptionId) }
    }

    fun onBodyChanged(body: String) {
        _state.update { it.copy(body = body) }
    }

    fun send() {
        val current = _state.value
        if (current.sending) return
        if (current.body.isBlank()) return
        val recipients = current.recipients
        if (recipients.isEmpty()) {
            _state.update { it.copy(errorRes = com.onemessages.app.R.string.invalid_number) }
            return
        }
        if (current.sims.isEmpty()) {
            _state.update { it.copy(errorRes = com.onemessages.app.R.string.error_no_sim) }
            return
        }
        _state.update { it.copy(sending = true, errorRes = null) }
        viewModelScope.launch {
            var firstThread: Long? = null
            var failures = 0
            var lastReason: SendFailureReason? = null
            for (recipient in recipients) {
                val threadId = conversationRepository.findThreadForAddress(recipient.address) ?: -1L
                when (
                    val outcome = smsSender.sendSms(
                        address = recipient.address,
                        body = current.body,
                        threadId = threadId,
                        subscriptionId = current.selectedSimId,
                    )
                ) {
                    is SendOutcome.Accepted -> if (firstThread == null) {
                        firstThread = conversationRepository.findThreadForAddress(recipient.address) ?: -1L
                    }
                    is SendOutcome.Rejected -> {
                        failures++
                        lastReason = outcome.reason
                    }
                }
            }
            _state.update { it.copy(sending = false) }
            when {
                recipients.size > 1 && failures == recipients.size -> {
                    _state.update { it.copy(errorRes = errorResFor(lastReason)) }
                }
                failures > 0 -> {
                    _state.update { it.copy(errorRes = com.onemessages.app.R.string.error_sms_failed) }
                }
                else -> {
                    val threadId = firstThread ?: -1L
                    if (threadId > 0) {
                        _events.value = NewMessageEvent.OpenConversation(threadId, recipients.first().address)
                    } else {
                        _events.value = NewMessageEvent.Sent
                    }
                }
            }
        }
    }

    private fun errorResFor(reason: SendFailureReason?): Int = when (reason) {
        SendFailureReason.NO_SIM -> com.onemessages.app.R.string.error_no_sim
        SendFailureReason.AIRPLANE_MODE,
        SendFailureReason.NO_SERVICE,
        SendFailureReason.RADIO_ERROR -> com.onemessages.app.R.string.error_radio_off
        SendFailureReason.PERMISSION_DENIED -> com.onemessages.app.R.string.permission_denied_body
        SendFailureReason.INVALID_RECIPIENT -> com.onemessages.app.R.string.invalid_number
        SendFailureReason.MESSAGE_TOO_LONG -> com.onemessages.app.R.string.error_sms_too_long
        else -> com.onemessages.app.R.string.error_sms_failed
    }

    fun clearEvent() {
        _events.value = null
    }

    private fun filterSuggestions(contacts: List<DeviceContact>, query: String): List<DeviceContact> {
        if (query.isBlank()) return contacts.take(6)
        val digits = PhoneNumbers.digitsOnly(query)
        return contacts.filter { contact ->
            contact.displayName.contains(query, ignoreCase = true) ||
                (digits.isNotEmpty() && contact.phones.any {
                    PhoneNumbers.digitsOnly(it.number).contains(digits)
                })
        }.take(8)
    }
}
