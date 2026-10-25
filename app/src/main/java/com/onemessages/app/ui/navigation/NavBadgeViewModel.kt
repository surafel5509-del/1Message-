package com.onemessages.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Drives the unread badge on the navigation bar without loading the whole inbox. */
@HiltViewModel
class NavBadgeViewModel @Inject constructor(
    conversationRepository: ConversationRepository,
) : ViewModel() {

    val unread: StateFlow<Int> = conversationRepository.observeUnreadTotal()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
