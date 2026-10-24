package com.onemessages.app.data.preferences

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentChoice { VIOLET, SKY, MINT, CORAL, AMBER, ROSE }

enum class BubbleStyle { ACCENT, SURFACE, HIGH_CONTRAST }

enum class DensityMode { COMPACT, COZY, COMFORTABLE }

enum class WallpaperStyle { PLAIN, SOFT_GRADIENT }

/** Everything persisted in DataStore. Defaults are the shipped look and behaviour. */
data class AppSettings(
    // Onboarding / roles
    val onboardingComplete: Boolean = false,

    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentChoice = AccentChoice.VIOLET,
    val bubbleStyle: BubbleStyle = BubbleStyle.ACCENT,
    val fontScale: Float = 1f,
    val density: DensityMode = DensityMode.COZY,
    val reducedMotion: Boolean = false,
    val haptics: Boolean = true,
    val wallpaper: WallpaperStyle = WallpaperStyle.PLAIN,
    val showAvatars: Boolean = true,
    val languageTag: String = "system",

    // Notifications
    val notificationsEnabled: Boolean = true,
    val showMessagePreview: Boolean = true,
    val hideContentOnLockScreen: Boolean = false,
    val groupNotifications: Boolean = true,
    val notificationSound: String? = null,
    val vibrationEnabled: Boolean = true,
    val remindersEnabled: Boolean = false,
    val reminderDelayMinutes: Int = 30,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val priorityNotifications: Boolean = true,

    // Privacy
    val appLockEnabled: Boolean = false,
    val otpDetectionEnabled: Boolean = true,
    val hideOtpDigits: Boolean = false,

    // Organization
    val categoriesEnabled: Boolean = false,
    val markReadOnOpen: Boolean = true,
    val smartRepliesEnabled: Boolean = true,

    // SIM & messaging
    val preferredSubscriptionId: Int = -1,
    val requestDeliveryReports: Boolean = false,
    val autoRetryFailedSends: Boolean = true,
    val autoDownloadMms: Boolean = true,
    val groupMessagingAsMms: Boolean = true,
    val signature: String = "",

    // Maintenance
    val lastBackupAt: Long = 0L,
    val lastReminderAt: Long = 0L,
) {
    val quietHoursValid: Boolean get() = quietStartMinute != quietEndMinute
}
