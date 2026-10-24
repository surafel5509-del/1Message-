package com.onemessages.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.R
import com.onemessages.app.core.Logx
import com.onemessages.app.data.backup.BackupCrypto
import com.onemessages.app.data.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val password: String = "",
    val confirmPassword: String = "",
    val busy: Boolean = false,
    val messageRes: Int? = null,
    val messageArg: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun setPassword(value: String) {
        _state.update { it.copy(password = value, messageRes = null) }
    }

    fun setConfirmPassword(value: String) {
        _state.update { it.copy(confirmPassword = value, messageRes = null) }
    }

    fun export(uri: Uri) {
        val current = _state.value
        if (current.password.length < BackupCrypto.MIN_PASSWORD_LENGTH) {
            _state.update { it.copy(messageRes = R.string.backup_password_short, isError = true) }
            return
        }
        if (current.password != current.confirmPassword) {
            _state.update { it.copy(messageRes = R.string.backup_password_short, isError = true) }
            return
        }
        _state.update { it.copy(busy = true, messageRes = null) }
        viewModelScope.launch {
            val password = current.password.toCharArray()
            val result = backupRepository.export(uri, password)
            password.fill('\u0000')
            _state.update {
                it.copy(
                    busy = false,
                    messageRes = if (result is BackupRepository.Result.Success) {
                        R.string.backup_export_success
                    } else {
                        R.string.backup_export_failed
                    },
                    isError = result !is BackupRepository.Result.Success,
                    password = "",
                    confirmPassword = "",
                )
            }
            Logx.i("Backup", "export result=${result::class.simpleName}")
        }
    }

    fun import(uri: Uri) {
        val current = _state.value
        if (current.password.length < BackupCrypto.MIN_PASSWORD_LENGTH) {
            _state.update { it.copy(messageRes = R.string.backup_password_short, isError = true) }
            return
        }
        _state.update { it.copy(busy = true, messageRes = null) }
        viewModelScope.launch {
            val password = current.password.toCharArray()
            val result = backupRepository.import(uri, password)
            password.fill('\u0000')
            _state.update { state ->
                result.fold(
                    onSuccess = { imported ->
                        state.copy(
                            busy = false,
                            messageRes = R.string.backup_import_success,
                            messageArg = imported.conversations.toString(),
                            isError = false,
                            password = "",
                        )
                    },
                    onFailure = { error ->
                        state.copy(
                            busy = false,
                            messageRes = if (error is BackupCrypto.WrongPasswordException) {
                                R.string.backup_wrong_password
                            } else {
                                R.string.backup_import_failed
                            },
                            isError = true,
                        )
                    },
                )
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(messageRes = null, messageArg = null) }
    }
}
