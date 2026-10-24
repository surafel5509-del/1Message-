package com.onemessages.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.data.repository.OrganizationRepository
import com.onemessages.app.model.CategoryRule
import com.onemessages.app.model.ConversationCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrganizationViewModel @Inject constructor(
    private val organizationRepository: OrganizationRepository,
) : ViewModel() {

    val rules: StateFlow<List<CategoryRule>> = organizationRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { organizationRepository.ensureSeeded() }
    }

    fun addRule(keyword: String, category: ConversationCategory) = viewModelScope.launch {
        organizationRepository.addRule(keyword, category)
    }

    fun toggleRule(rule: CategoryRule, enabled: Boolean) = viewModelScope.launch {
        organizationRepository.setRuleEnabled(rule, enabled)
    }

    fun deleteRule(rule: CategoryRule) = viewModelScope.launch {
        organizationRepository.deleteRule(rule)
    }
}
