package com.onemessages.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.contacts.ContactsRepository
import com.onemessages.app.model.DeviceContact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<DeviceContact> = emptyList(),
    val query: String = "",
    val hasPermission: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val refreshing = MutableStateFlow(true)

    val state: StateFlow<ContactsUiState> = combine(
        contactsRepository.contacts,
        query,
        refreshing,
    ) { contacts, currentQuery, loading ->
        ContactsUiState(
            contacts = if (currentQuery.isBlank()) {
                contacts
            } else {
                contactsRepository.search(currentQuery)
            },
            query = currentQuery,
            hasPermission = contactsRepository.hasPermission(),
            loading = loading && contacts.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    init {
        refresh()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            contactsRepository.refresh()
            refreshing.value = false
        }
    }
}
