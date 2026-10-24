package com.onemessages.app.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.contacts.ContactsRepository
import com.onemessages.app.core.Logx
import com.onemessages.app.data.preferences.AppSettings
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.repository.ProviderChangeNotifier
import com.onemessages.app.model.DeviceTelephonyState
import com.onemessages.app.model.SmsRoleState
import com.onemessages.app.permissions.AppPermissions
import com.onemessages.app.permissions.PermissionGroup
import com.onemessages.app.telephony.SmsRoleManager
import com.onemessages.app.telephony.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PermissionState(
    val sms: Boolean = false,
    val contacts: Boolean = false,
    val notifications: Boolean = false,
    val phoneState: Boolean = false,
) {
    val onboardingReady: Boolean get() = sms
}

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val roleManager: SmsRoleManager,
    private val subscriptions: SubscriptionRepository,
    private val contactsRepository: ContactsRepository,
    private val providerNotifier: ProviderChangeNotifier,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _roleState = MutableStateFlow(roleManager.state())
    val roleState: StateFlow<SmsRoleState> = _roleState.asStateFlow()

    private val _deviceState = MutableStateFlow(
        DeviceTelephonyState(
            radioState = subscriptions.radioState(),
            sims = subscriptions.sims(),
            smsRoleState = roleManager.state(),
            canSendSms = false,
            canSendMms = false,
        )
    )
    val deviceState: StateFlow<DeviceTelephonyState> = _deviceState.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** True while the process is still deciding what to show (splash). */
    private val _initialising = MutableStateFlow(true)
    val initialising: StateFlow<Boolean> = _initialising.asStateFlow()

    init {
        providerNotifier.start()
        viewModelScope.launch {
            runCatching { contactsRepository.refresh() }
            var firstEmission = true
            settingsRepository.settings.collect { current ->
                if (firstEmission) {
                    firstEmission = false
                    // Lock on cold start only when the user enabled app lock.
                    _locked.value = current.appLockEnabled
                } else if (!current.appLockEnabled) {
                    _locked.value = false
                }
                applyLanguageIfChanged(current.languageTag)
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(450)
            _initialising.value = false
        }
        refreshTelephony()
    }

    fun refreshPermissions() {
        _permissions.value = readPermissions()
        refreshTelephony()
    }

    fun refreshTelephony() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val role = roleManager.state()
                contactsRepository.refresh()
                role to subscriptions.deviceState()
            }
            _roleState.value = snapshot.first
            _deviceState.value = snapshot.second
        }
    }

    fun onSmsRoleResult(granted: Boolean) {
        Logx.i("Role", "sms role result granted=$granted")
        refreshTelephony()
    }

    /** The official ROLE_SMS request intent, or null when the role is already held. */
    fun createRoleIntent(): android.content.Intent? = roleManager.createRequestIntent()

    fun createLegacyRoleIntent(): android.content.Intent = roleManager.legacyChangeDefaultIntent()

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingComplete(true) }
    }

    fun unlock() {
        _locked.value = false
    }

    fun lockIfEnabled() {
        _locked.value = settings.value.appLockEnabled
    }

    private fun readPermissions(): PermissionState = PermissionState(
        sms = AppPermissions.isGranted(context, PermissionGroup.SMS),
        contacts = AppPermissions.isGranted(context, PermissionGroup.CONTACTS),
        notifications = AppPermissions.isGranted(context, PermissionGroup.NOTIFICATIONS),
        phoneState = AppPermissions.isGranted(context, PermissionGroup.PHONE_STATE),
    )

    fun requestNotificationsHint() {
        // Actual request happens in the UI layer where the launcher lives.
    }

    private var appliedLanguage: String? = null

    private fun applyLanguageIfChanged(tag: String) {
        if (appliedLanguage == tag) return
        appliedLanguage = tag
        val locales = if (tag == "system" || tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        runCatching {
            AppCompatDelegate.setApplicationLocales(locales)
        }.onFailure { Logx.w("Locale", "language change failed", it) }
    }
}
