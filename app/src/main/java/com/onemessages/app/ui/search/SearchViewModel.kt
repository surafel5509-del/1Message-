package com.onemessages.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.contacts.ContactsRepository
import com.onemessages.app.core.Logx
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.data.repository.SearchResults
import com.onemessages.app.model.DeviceContact
import com.onemessages.app.model.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class SearchScope { ALL, MESSAGES, CONTACTS }

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val messages: List<SearchHit> = emptyList(),
    val contacts: List<DeviceContact> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    val dateSearchLabel: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow
                .debounce(220)
                .distinctUntilChanged()
                .collect { query -> runSearch(query) }
        }
    }

    fun onQueryChanged(query: String) {
        _state.value = _state.value.copy(query = query, searching = query.length >= 2)
        queryFlow.value = query
    }

    fun setScope(scope: SearchScope) {
        _state.value = _state.value.copy(scope = scope)
    }

    private suspend fun runSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.value = _state.value.copy(
                messages = emptyList(),
                contacts = emptyList(),
                searching = false,
                searched = false,
                dateSearchLabel = null,
            )
            return
        }
        val contacts = contactsRepository.search(trimmed)
        val dateRange = parseDateQuery(trimmed)
        val messages: List<SearchHit> = runCatching {
            if (dateRange != null) {
                conversationRepository.searchByDateRange(dateRange.first, dateRange.second)
            } else {
                conversationRepository.search(trimmed).messages
            }
        }.onFailure { Logx.w("Search", "search failed", it) }
            .getOrElse { emptyList() }
        _state.value = _state.value.copy(
            messages = messages,
            contacts = contacts,
            searching = false,
            searched = true,
            dateSearchLabel = if (dateRange != null) trimmed else null,
        )
    }

    /** Very small, explicit natural-language date parser. English keywords only. */
    private fun parseDateQuery(query: String): Pair<Long, Long>? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (start, end) = when (query.lowercase().trim()) {
            "today" -> today to today
            "yesterday" -> today.minusDays(1) to today.minusDays(1)
            "this week" -> today.minusDays(6) to today
            "last week" -> today.minusDays(13) to today.minusDays(7)
            "this month" -> today.withDayOfMonth(1) to today
            else -> return null
        }
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to
            end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    }
}
