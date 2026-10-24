package com.onemessages.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.data.local.DiagnosticEntity
import com.onemessages.app.data.repository.DiagnosticsRepository
import com.onemessages.app.data.repository.StorageRepository
import com.onemessages.app.data.repository.StorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
) : ViewModel() {

    private val _usage = MutableStateFlow(StorageUsage(0, 0, 0))
    val usage: StateFlow<StorageUsage> = _usage.asStateFlow()

    private val _environment = MutableStateFlow<DiagnosticsRepository.EnvironmentSnapshot?>(null)
    val environment: StateFlow<DiagnosticsRepository.EnvironmentSnapshot?> = _environment.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    val entries: StateFlow<List<DiagnosticEntity>> = diagnosticsRepository.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _usage.value = storageRepository.usage()
            _environment.value = diagnosticsRepository.environment()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            storageRepository.clearCache()
            _usage.value = storageRepository.usage()
            _message.value = com.onemessages.app.R.string.storage_cleared
        }
    }

    fun clearDiagnostics() {
        viewModelScope.launch {
            diagnosticsRepository.clear()
            _message.value = com.onemessages.app.R.string.diagnostics_empty
        }
    }

    fun exportDiagnostics(onReady: (String) -> Unit) {
        viewModelScope.launch {
            onReady(diagnosticsRepository.exportText())
            _message.value = com.onemessages.app.R.string.diagnostics_copied
        }
    }

    fun format(bytes: Long): String = storageRepository.format(bytes)

    fun clearMessage() {
        _message.value = null
    }
}
