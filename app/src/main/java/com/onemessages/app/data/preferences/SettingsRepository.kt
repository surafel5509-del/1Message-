package com.onemessages.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onemessages.app.core.Logx
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Typed wrapper over DataStore. Every setter is a small transaction so concurrent updates
 * from the UI and from background work cannot clobber each other.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val bubbleStyle = stringPreferencesKey("bubble_style")
        val fontScale = floatPreferencesKey("font_scale")
        val density = stringPreferencesKey("density")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val haptics = booleanPreferencesKey("haptics")
        val wallpaper = stringPreferencesKey("wallpaper")
        val showAvatars = booleanPreferencesKey("show_avatars")
        val languageTag = stringPreferencesKey("language_tag")

        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val showMessagePreview = booleanPreferencesKey("show_message_preview")
        val hideContentOnLock = booleanPreferencesKey("hide_content_on_lock")
        val groupNotifications = booleanPreferencesKey("group_notifications")
        val notificationSound = stringPreferencesKey("notification_sound")
        val vibration = booleanPreferencesKey("vibration")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val reminderDelay = intPreferencesKey("reminder_delay_minutes")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start_minute")
        val quietEnd = intPreferencesKey("quiet_end_minute")
        val priorityNotifications = booleanPreferencesKey("priority_notifications")

        val appLock = booleanPreferencesKey("app_lock")
        val otpDetection = booleanPreferencesKey("otp_detection")
        val hideOtpDigits = booleanPreferencesKey("hide_otp_digits")

        val categoriesEnabled = booleanPreferencesKey("categories_enabled")
        val markReadOnOpen = booleanPreferencesKey("mark_read_on_open")
        val smartReplies = booleanPreferencesKey("smart_replies")

        val preferredSubId = intPreferencesKey("preferred_sub_id")
        val deliveryReports = booleanPreferencesKey("delivery_reports")
        val autoRetry = booleanPreferencesKey("auto_retry")
        val autoDownloadMms = booleanPreferencesKey("auto_download_mms")
        val groupAsMms = booleanPreferencesKey("group_as_mms")
        val signature = stringPreferencesKey("signature")

        val lastBackupAt = longPreferencesKey("last_backup_at")
        val lastReminderAt = longPreferencesKey("last_reminder_at")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { throwable ->
            // A corrupted preferences file must not brick the app: fall back to defaults.
            if (throwable is IOException) {
                Logx.e("Settings", "preferences read failed, using defaults", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { prefs -> prefs.toSettings() }

    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboardingComplete] = value }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setAccent(value: AccentChoice) = edit { it[Keys.accent] = value.name }
    suspend fun setBubbleStyle(value: BubbleStyle) = edit { it[Keys.bubbleStyle] = value.name }
    suspend fun setFontScale(value: Float) = edit { it[Keys.fontScale] = value.coerceIn(0.8f, 1.6f) }
    suspend fun setDensity(value: DensityMode) = edit { it[Keys.density] = value.name }
    suspend fun setReducedMotion(value: Boolean) = edit { it[Keys.reducedMotion] = value }
    suspend fun setHaptics(value: Boolean) = edit { it[Keys.haptics] = value }
    suspend fun setWallpaper(value: WallpaperStyle) = edit { it[Keys.wallpaper] = value.name }
    suspend fun setShowAvatars(value: Boolean) = edit { it[Keys.showAvatars] = value }
    suspend fun setLanguageTag(value: String) = edit { it[Keys.languageTag] = value }

    suspend fun setNotificationsEnabled(value: Boolean) = edit { it[Keys.notificationsEnabled] = value }
    suspend fun setShowMessagePreview(value: Boolean) = edit { it[Keys.showMessagePreview] = value }
    suspend fun setHideContentOnLock(value: Boolean) = edit { it[Keys.hideContentOnLock] = value }
    suspend fun setGroupNotifications(value: Boolean) = edit { it[Keys.groupNotifications] = value }
    suspend fun setNotificationSound(value: String?) = edit { prefs ->
        if (value == null) prefs.remove(Keys.notificationSound) else prefs[Keys.notificationSound] = value
    }

    suspend fun setVibration(value: Boolean) = edit { it[Keys.vibration] = value }
    suspend fun setRemindersEnabled(value: Boolean) = edit { it[Keys.remindersEnabled] = value }
    suspend fun setReminderDelayMinutes(value: Int) =
        edit { it[Keys.reminderDelay] = value.coerceIn(5, 24 * 60) }

    suspend fun setQuietHoursEnabled(value: Boolean) = edit { it[Keys.quietEnabled] = value }
    suspend fun setQuietStart(minuteOfDay: Int) = edit { it[Keys.quietStart] = minuteOfDay.mod(24 * 60) }
    suspend fun setQuietEnd(minuteOfDay: Int) = edit { it[Keys.quietEnd] = minuteOfDay.mod(24 * 60) }
    suspend fun setPriorityNotifications(value: Boolean) = edit { it[Keys.priorityNotifications] = value }

    suspend fun setAppLock(value: Boolean) = edit { it[Keys.appLock] = value }
    suspend fun setOtpDetection(value: Boolean) = edit { it[Keys.otpDetection] = value }
    suspend fun setHideOtpDigits(value: Boolean) = edit { it[Keys.hideOtpDigits] = value }

    suspend fun setCategoriesEnabled(value: Boolean) = edit { it[Keys.categoriesEnabled] = value }
    suspend fun setMarkReadOnOpen(value: Boolean) = edit { it[Keys.markReadOnOpen] = value }

    suspend fun setSmartReplies(value: Boolean) = edit { it[Keys.smartReplies] = value }

    suspend fun setPreferredSubscriptionId(value: Int) = edit { it[Keys.preferredSubId] = value }
    suspend fun setDeliveryReports(value: Boolean) = edit { it[Keys.deliveryReports] = value }
    suspend fun setAutoRetry(value: Boolean) = edit { it[Keys.autoRetry] = value }
    suspend fun setAutoDownloadMms(value: Boolean) = edit { it[Keys.autoDownloadMms] = value }
    suspend fun setGroupAsMms(value: Boolean) = edit { it[Keys.groupAsMms] = value }
    suspend fun setSignature(value: String) = edit { it[Keys.signature] = value.take(280) }

    suspend fun setLastBackupAt(value: Long) = edit { it[Keys.lastBackupAt] = value }
    suspend fun setLastReminderAt(value: Long) = edit { it[Keys.lastReminderAt] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        runCatching { context.settingsDataStore.edit(block) }
            .onFailure { Logx.e("Settings", "preference write failed", it) }
    }

    private fun Preferences.toSettings(): AppSettings {
        fun string(key: Preferences.Key<String>, fallback: String) = this[key] ?: fallback
        fun bool(key: Preferences.Key<Boolean>, fallback: Boolean) = this[key] ?: fallback
        fun int(key: Preferences.Key<Int>, fallback: Int) = this[key] ?: fallback
        return AppSettings(
            onboardingComplete = bool(Keys.onboardingComplete, false),
            themeMode = runCatching { ThemeMode.valueOf(string(Keys.themeMode, "SYSTEM")) }
                .getOrDefault(ThemeMode.SYSTEM),
            accent = runCatching { AccentChoice.valueOf(string(Keys.accent, "VIOLET")) }
                .getOrDefault(AccentChoice.VIOLET),
            bubbleStyle = runCatching { BubbleStyle.valueOf(string(Keys.bubbleStyle, "ACCENT")) }
                .getOrDefault(BubbleStyle.ACCENT),
            fontScale = (this[Keys.fontScale] ?: 1f).coerceIn(0.8f, 1.6f),
            density = runCatching { DensityMode.valueOf(string(Keys.density, "COZY")) }
                .getOrDefault(DensityMode.COZY),
            reducedMotion = bool(Keys.reducedMotion, false),
            haptics = bool(Keys.haptics, true),
            wallpaper = runCatching { WallpaperStyle.valueOf(string(Keys.wallpaper, "PLAIN")) }
                .getOrDefault(WallpaperStyle.PLAIN),
            showAvatars = bool(Keys.showAvatars, true),
            languageTag = string(Keys.languageTag, "system"),
            notificationsEnabled = bool(Keys.notificationsEnabled, true),
            showMessagePreview = bool(Keys.showMessagePreview, true),
            hideContentOnLockScreen = bool(Keys.hideContentOnLock, false),
            groupNotifications = bool(Keys.groupNotifications, true),
            notificationSound = this[Keys.notificationSound],
            vibrationEnabled = bool(Keys.vibration, true),
            remindersEnabled = bool(Keys.remindersEnabled, false),
            reminderDelayMinutes = int(Keys.reminderDelay, 30),
            quietHoursEnabled = bool(Keys.quietEnabled, false),
            quietStartMinute = int(Keys.quietStart, 22 * 60),
            quietEndMinute = int(Keys.quietEnd, 7 * 60),
            priorityNotifications = bool(Keys.priorityNotifications, true),
            appLockEnabled = bool(Keys.appLock, false),
            otpDetectionEnabled = bool(Keys.otpDetection, true),
            hideOtpDigits = bool(Keys.hideOtpDigits, false),
            categoriesEnabled = bool(Keys.categoriesEnabled, false),
            markReadOnOpen = bool(Keys.markReadOnOpen, true),
            smartRepliesEnabled = bool(Keys.smartReplies, true),
            preferredSubscriptionId = int(Keys.preferredSubId, -1),
            requestDeliveryReports = bool(Keys.deliveryReports, false),
            autoRetryFailedSends = bool(Keys.autoRetry, true),
            autoDownloadMms = bool(Keys.autoDownloadMms, true),
            groupMessagingAsMms = bool(Keys.groupAsMms, true),
            signature = string(Keys.signature, ""),
            lastBackupAt = this[Keys.lastBackupAt] ?: 0L,
            lastReminderAt = this[Keys.lastReminderAt] ?: 0L,
        )
    }
}
