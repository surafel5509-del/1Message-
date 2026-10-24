package com.onemessages.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemessages.app.data.backup.BackupRepository
import com.onemessages.app.data.preferences.AccentChoice
import com.onemessages.app.data.preferences.AppSettings
import com.onemessages.app.data.preferences.BubbleStyle
import com.onemessages.app.data.preferences.DensityMode
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.preferences.ThemeMode
import com.onemessages.app.data.preferences.WallpaperStyle
import com.onemessages.app.data.repository.ConversationRepository
import com.onemessages.app.data.repository.ProviderChangeNotifier
import com.onemessages.app.model.DeviceTelephonyState
import com.onemessages.app.model.SmsRoleState
import com.onemessages.app.notifications.MessageNotifier
import com.onemessages.app.telephony.SmsRoleManager
import com.onemessages.app.telephony.SubscriptionRepository
import com.onemessages.app.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val settingsRepository: SettingsRepository,
    private val roleManager: SmsRoleManager,
    private val subscriptions: SubscriptionRepository,
    private val notifier: MessageNotifier,
    private val backupRepository: BackupRepository,
    private val reminderScheduler: ReminderScheduler,
    private val providerNotifier: ProviderChangeNotifier,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _roleState = MutableStateFlow(roleManager.state())
    val roleState: StateFlow<SmsRoleState> = _roleState.asStateFlow()

    private val _deviceState = MutableStateFlow(subscriptions.deviceState())
    val deviceState: StateFlow<DeviceTelephonyState> = _deviceState.asStateFlow()

    private val _testResult = MutableStateFlow<Boolean?>(null)
    val testResult: StateFlow<Boolean?> = _testResult.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** Conversations whose sender the user has blocked; blocking is always reversible. */
    val blockedSenders: StateFlow<List<com.onemessages.app.model.Conversation>> =
        conversationRepository.observeBlockedThreads()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unblockSender(threadId: Long) = launch {
        conversationRepository.setBlocked(threadId, false)
        providerNotifier.notifyChanged()
        _message.value = com.onemessages.app.R.string.blocked_sender_unblocked
    }

    fun refreshTelephony() {
        _roleState.value = roleManager.state()
        _deviceState.value = subscriptions.deviceState()
    }

    fun setTheme(mode: ThemeMode) = launch { settingsRepository.setThemeMode(mode) }
    fun setAccent(accent: AccentChoice) = launch { settingsRepository.setAccent(accent) }
    fun setBubbleStyle(style: BubbleStyle) = launch { settingsRepository.setBubbleStyle(style) }
    fun setFontScale(scale: Float) = launch { settingsRepository.setFontScale(scale) }
    fun setDensity(density: DensityMode) = launch { settingsRepository.setDensity(density) }
    fun setReducedMotion(value: Boolean) = launch { settingsRepository.setReducedMotion(value) }
    fun setHaptics(value: Boolean) = launch { settingsRepository.setHaptics(value) }
    fun setWallpaper(style: WallpaperStyle) = launch { settingsRepository.setWallpaper(style) }
    fun setShowAvatars(value: Boolean) = launch { settingsRepository.setShowAvatars(value) }
    fun setLanguage(tag: String) = launch { settingsRepository.setLanguageTag(tag) }

    fun setNotificationsEnabled(value: Boolean) = launch { settingsRepository.setNotificationsEnabled(value) }
    fun setShowPreview(value: Boolean) = launch { settingsRepository.setShowMessagePreview(value) }
    fun setHideOnLock(value: Boolean) = launch { settingsRepository.setHideContentOnLock(value) }
    fun setGroupNotifications(value: Boolean) = launch { settingsRepository.setGroupNotifications(value) }
    fun setNotificationSound(uri: String?) = launch {
        settingsRepository.setNotificationSound(uri)
        com.onemessages.app.notifications.NotificationChannels.pruneCustomSoundChannels(context, null)
    }
    fun setVibration(value: Boolean) = launch { settingsRepository.setVibration(value) }
    fun setRemindersEnabled(value: Boolean) = launch {
        settingsRepository.setRemindersEnabled(value)
        reminderScheduler.syncSchedule()
    }
    fun setReminderDelay(minutes: Int) = launch {
        settingsRepository.setReminderDelayMinutes(minutes)
        reminderScheduler.syncSchedule()
    }
    fun setQuietHoursEnabled(value: Boolean) = launch { settingsRepository.setQuietHoursEnabled(value) }
    fun setQuietStart(minute: Int) = launch { settingsRepository.setQuietStart(minute) }
    fun setQuietEnd(minute: Int) = launch { settingsRepository.setQuietEnd(minute) }
    fun setPriorityNotifications(value: Boolean) = launch {
        settingsRepository.setPriorityNotifications(value)
    }

    fun setAppLock(value: Boolean) = launch { settingsRepository.setAppLock(value) }
    fun setOtpDetection(value: Boolean) = launch { settingsRepository.setOtpDetection(value) }
    fun setHideOtpDigits(value: Boolean) = launch { settingsRepository.setHideOtpDigits(value) }
    fun setCategoriesEnabled(value: Boolean) = launch { settingsRepository.setCategoriesEnabled(value) }
    fun setMarkReadOnOpen(value: Boolean) = launch { settingsRepository.setMarkReadOnOpen(value) }

    fun setPreferredSubscription(id: Int) = launch { settingsRepository.setPreferredSubscriptionId(id) }
    fun setDeliveryReports(value: Boolean) = launch { settingsRepository.setDeliveryReports(value) }
    fun setAutoRetry(value: Boolean) = launch { settingsRepository.setAutoRetry(value) }
    fun setAutoDownloadMms(value: Boolean) = launch { settingsRepository.setAutoDownloadMms(value) }
    fun setGroupAsMms(value: Boolean) = launch { settingsRepository.setGroupAsMms(value) }
    fun setSignature(value: String) = launch { settingsRepository.setSignature(value) }

    fun setSmartReplies(value: Boolean) = launch { settingsRepository.setSmartReplies(value) }

    fun sendTestNotification() = launch {
        _testResult.value = notifier.showTestNotification()
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun clearAppData() = launch {
        backupRepository.clearAppData()
        providerNotifier.notifyChanged()
        _message.value = com.onemessages.app.R.string.privacy_delete_all_done
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
