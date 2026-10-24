package com.onemessages.app.data.backup

import android.content.Context
import android.net.Uri
import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.CategoryRuleDao
import com.onemessages.app.data.local.ConversationMetaDao
import com.onemessages.app.data.local.ConversationMetaEntity
import com.onemessages.app.data.local.DraftDao
import com.onemessages.app.data.local.DraftEntity
import com.onemessages.app.data.local.StarredMessageDao
import com.onemessages.app.data.local.StarredMessageEntity
import com.onemessages.app.data.preferences.AccentChoice
import com.onemessages.app.data.preferences.BubbleStyle
import com.onemessages.app.data.preferences.DensityMode
import com.onemessages.app.data.preferences.SettingsRepository
import com.onemessages.app.data.preferences.ThemeMode
import com.onemessages.app.data.preferences.WallpaperStyle
import com.onemessages.app.data.repository.OrganizationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted, local-only backup of app metadata.
 *
 * What is included: pins, archives, mutes, blocked senders, drafts, starred messages,
 * category rules and user settings. What is NOT included: the messages themselves. They live
 * in Android's provider and are not ours to duplicate into a portable file. The UI says so.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metaDao: ConversationMetaDao,
    private val draftDao: DraftDao,
    private val starredDao: StarredMessageDao,
    private val ruleDao: CategoryRuleDao,
    private val organizationRepository: OrganizationRepository,
    private val settingsRepository: SettingsRepository,
) {

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String, val wrongPassword: Boolean = false) : Result
    }

    data class ImportResult(
        val conversations: Int,
        val drafts: Int,
        val rules: Int,
    )

    suspend fun export(uri: Uri, password: CharArray): Result = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload().toString().toByteArray(Charsets.UTF_8)
            val encrypted = BackupCrypto.encrypt(payload, password)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(encrypted)
                output.flush()
            } ?: error("cannot open destination")
            BackupCrypto.secureWipe(payload)
            settingsRepository.setLastBackupAt(System.currentTimeMillis())
            Logx.i("Backup", "encrypted backup written")
            Result.Success
        }.getOrElse { error ->
            Logx.e("Backup", "export failed", error)
            Result.Failure(error.message ?: "export failed")
        }
    }

    suspend fun import(uri: Uri, password: CharArray): kotlin.Result<ImportResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val blob = context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_BACKUP_BYTES) error("backup file too large")
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                } ?: error("cannot read file")
                val plaintext = BackupCrypto.decrypt(blob, password)
                val json = JSONObject(String(plaintext, Charsets.UTF_8))
                BackupCrypto.secureWipe(plaintext)
                applyPayload(json)
            }.onFailure { error ->
                Logx.w(
                    "Backup",
                    if (error is BackupCrypto.WrongPasswordException) {
                        "backup rejected: wrong password or corrupted file"
                    } else {
                        "backup import failed"
                    },
                    error,
                )
            }
        }

    private suspend fun buildPayload(): JSONObject {
        val meta = metaDao.snapshot()
        val drafts = draftDao.snapshot()
        val starred = starredDao.snapshot()
        val rules = ruleDao.observeAll().first()
        val settings = settingsRepository.settings.first()

        val metaArray = JSONArray()
        meta.forEach { entity ->
            metaArray.put(
                JSONObject().apply {
                    put("threadId", entity.threadId)
                    put("isPinned", entity.isPinned)
                    put("isArchived", entity.isArchived)
                    put("isStarred", entity.isStarred)
                    put("isMuted", entity.isMuted)
                    put("priority", entity.priority)
                    put("isBlocked", entity.isBlocked)
                    entity.customSound?.let { put("customSound", it) }
                    entity.customTitle?.let { put("customTitle", it) }
                    entity.categoryOverride?.let { put("categoryOverride", it) }
                }
            )
        }
        val draftArray = JSONArray()
        drafts.forEach { draft ->
            draftArray.put(
                JSONObject().apply {
                    put("threadId", draft.threadId)
                    put("text", draft.text)
                    put("updatedAt", draft.updatedAt)
                }
            )
        }
        val starredArray = JSONArray()
        starred.forEach { entry ->
            starredArray.put(
                JSONObject().apply {
                    put("messageId", entry.messageId)
                    put("threadId", entry.threadId)
                    put("source", entry.source)
                    put("addedAt", entry.addedAt)
                }
            )
        }
        val ruleArray = JSONArray()
        rules.forEach { rule ->
            ruleArray.put(
                JSONObject().apply {
                    put("keyword", rule.keyword)
                    put("category", rule.category)
                    put("enabled", rule.enabled)
                    put("isBuiltIn", rule.isBuiltIn)
                }
            )
        }
        val settingsObject = JSONObject().apply {
            put("themeMode", settings.themeMode.name)
            put("accent", settings.accent.name)
            put("bubbleStyle", settings.bubbleStyle.name)
            put("fontScale", settings.fontScale.toDouble())
            put("density", settings.density.name)
            put("reducedMotion", settings.reducedMotion)
            put("haptics", settings.haptics)
            put("wallpaper", settings.wallpaper.name)
            put("showAvatars", settings.showAvatars)
            put("notificationsEnabled", settings.notificationsEnabled)
            put("showMessagePreview", settings.showMessagePreview)
            put("hideContentOnLockScreen", settings.hideContentOnLockScreen)
            put("groupNotifications", settings.groupNotifications)
            put("vibration", settings.vibrationEnabled)
            put("remindersEnabled", settings.remindersEnabled)
            put("reminderDelayMinutes", settings.reminderDelayMinutes)
            put("quietHoursEnabled", settings.quietHoursEnabled)
            put("quietStartMinute", settings.quietStartMinute)
            put("quietEndMinute", settings.quietEndMinute)
            put("priorityNotifications", settings.priorityNotifications)
            put("otpDetection", settings.otpDetectionEnabled)
            put("hideOtpDigits", settings.hideOtpDigits)
            put("categoriesEnabled", settings.categoriesEnabled)
            put("preferredSubscriptionId", settings.preferredSubscriptionId)
            put("deliveryReports", settings.requestDeliveryReports)
            put("autoRetry", settings.autoRetryFailedSends)
            put("autoDownloadMms", settings.autoDownloadMms)
            put("signature", settings.signature)
        }
        return JSONObject().apply {
            put("format", "1messages-backup")
            put("version", BACKUP_VERSION)
            put("createdAt", System.currentTimeMillis())
            put("conversations", metaArray)
            put("drafts", draftArray)
            put("starred", starredArray)
            put("rules", ruleArray)
            put("settings", settingsObject)
        }
    }

    private suspend fun applyPayload(json: JSONObject): ImportResult {
        if (json.optString("format") != "1messages-backup") error("not a 1Messages backup")
        if (json.optInt("version", 0) > BACKUP_VERSION) error("backup is from a newer version")

        val conversationArray = json.optJSONArray("conversations") ?: JSONArray()
        val metaEntities = ArrayList<ConversationMetaEntity>(conversationArray.length())
        for (index in 0 until conversationArray.length()) {
            val item = conversationArray.optJSONObject(index) ?: continue
            metaEntities += ConversationMetaEntity(
                threadId = item.optLong("threadId"),
                isPinned = item.optBoolean("isPinned"),
                isArchived = item.optBoolean("isArchived"),
                isStarred = item.optBoolean("isStarred"),
                isMuted = item.optBoolean("isMuted"),
                priority = item.optBoolean("priority"),
                isBlocked = item.optBoolean("isBlocked"),
                customSound = item.optString("customSound").takeIf { it.isNotBlank() },
                customTitle = item.optString("customTitle").takeIf { it.isNotBlank() },
                categoryOverride = item.optString("categoryOverride").takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            )
        }
        val draftArray = json.optJSONArray("drafts") ?: JSONArray()
        val drafts = ArrayList<DraftEntity>(draftArray.length())
        for (index in 0 until draftArray.length()) {
            val item = draftArray.optJSONObject(index) ?: continue
            val text = item.optString("text")
            if (text.isBlank()) continue
            drafts += DraftEntity(
                threadId = item.optLong("threadId"),
                text = text,
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        val starredArray = json.optJSONArray("starred") ?: JSONArray()
        val starred = ArrayList<StarredMessageEntity>(starredArray.length())
        for (index in 0 until starredArray.length()) {
            val item = starredArray.optJSONObject(index) ?: continue
            starred += StarredMessageEntity(
                messageId = item.optLong("messageId"),
                threadId = item.optLong("threadId"),
                source = item.optString("source", "SMS"),
                addedAt = item.optLong("addedAt", System.currentTimeMillis()),
            )
        }
        val ruleArray = json.optJSONArray("rules") ?: JSONArray()
        val rules = ArrayList<com.onemessages.app.data.local.CategoryRuleEntity>(ruleArray.length())
        for (index in 0 until ruleArray.length()) {
            val item = ruleArray.optJSONObject(index) ?: continue
            val keyword = item.optString("keyword").trim()
            if (keyword.isEmpty()) continue
            rules += com.onemessages.app.data.local.CategoryRuleEntity(
                keyword = keyword,
                category = item.optString("category", "PERSONAL"),
                enabled = item.optBoolean("enabled", true),
                isBuiltIn = item.optBoolean("isBuiltIn"),
            )
        }

        metaDao.clear()
        metaDao.insertAll(metaEntities)
        draftDao.clear()
        draftDao.insertAll(drafts)
        starredDao.clear()
        starredDao.insertAll(starred)
        if (rules.isNotEmpty()) {
            ruleDao.clearUserRules()
            ruleDao.insertAll(rules.filter { it.isBuiltIn })
            ruleDao.insertAll(rules.filterNot { it.isBuiltIn })
        }
        json.optJSONObject("settings")?.let { restoreSettings(it) }
        organizationRepository.ensureSeeded()
        return ImportResult(metaEntities.size, drafts.size, rules.size)
    }

    private suspend fun restoreSettings(settings: JSONObject) {
        with(settingsRepository) {
            runCatching { setThemeMode(ThemeMode.valueOf(settings.getString("themeMode"))) }
            runCatching { setAccent(AccentChoice.valueOf(settings.getString("accent"))) }
            runCatching { setBubbleStyle(BubbleStyle.valueOf(settings.getString("bubbleStyle"))) }
            runCatching { setDensity(DensityMode.valueOf(settings.getString("density"))) }
            runCatching { setWallpaper(WallpaperStyle.valueOf(settings.getString("wallpaper"))) }
            settings.optDouble("fontScale", 1.0).takeIf { !it.isNaN() }?.let {
                setFontScale(it.toFloat())
            }
            if (settings.has("reducedMotion")) setReducedMotion(settings.getBoolean("reducedMotion"))
            if (settings.has("haptics")) setHaptics(settings.getBoolean("haptics"))
            if (settings.has("showAvatars")) setShowAvatars(settings.getBoolean("showAvatars"))
            if (settings.has("notificationsEnabled")) {
                setNotificationsEnabled(settings.getBoolean("notificationsEnabled"))
            }
            if (settings.has("showMessagePreview")) {
                setShowMessagePreview(settings.getBoolean("showMessagePreview"))
            }
            if (settings.has("hideContentOnLockScreen")) {
                setHideContentOnLock(settings.getBoolean("hideContentOnLockScreen"))
            }
            if (settings.has("groupNotifications")) {
                setGroupNotifications(settings.getBoolean("groupNotifications"))
            }
            if (settings.has("vibration")) setVibration(settings.getBoolean("vibration"))
            if (settings.has("remindersEnabled")) {
                setRemindersEnabled(settings.getBoolean("remindersEnabled"))
            }
            if (settings.has("reminderDelayMinutes")) {
                setReminderDelayMinutes(settings.getInt("reminderDelayMinutes"))
            }
            if (settings.has("quietHoursEnabled")) {
                setQuietHoursEnabled(settings.getBoolean("quietHoursEnabled"))
            }
            if (settings.has("quietStartMinute")) setQuietStart(settings.getInt("quietStartMinute"))
            if (settings.has("quietEndMinute")) setQuietEnd(settings.getInt("quietEndMinute"))
            if (settings.has("priorityNotifications")) {
                setPriorityNotifications(settings.getBoolean("priorityNotifications"))
            }
            if (settings.has("otpDetection")) setOtpDetection(settings.getBoolean("otpDetection"))
            if (settings.has("hideOtpDigits")) setHideOtpDigits(settings.getBoolean("hideOtpDigits"))
            if (settings.has("categoriesEnabled")) {
                setCategoriesEnabled(settings.getBoolean("categoriesEnabled"))
            }
            if (settings.has("preferredSubscriptionId")) {
                setPreferredSubscriptionId(settings.getInt("preferredSubscriptionId"))
            }
            if (settings.has("deliveryReports")) setDeliveryReports(settings.getBoolean("deliveryReports"))
            if (settings.has("autoRetry")) setAutoRetry(settings.getBoolean("autoRetry"))
            if (settings.has("autoDownloadMms")) setAutoDownloadMms(settings.getBoolean("autoDownloadMms"))
            settings.optString("signature").takeIf { it.isNotEmpty() }?.let { setSignature(it) }
        }
    }

    /** Wipes app-owned metadata only. System SMS rows are managed by Android. */
    suspend fun clearAppData() = withContext(Dispatchers.IO) {
        metaDao.clear()
        draftDao.clear()
        starredDao.clear()
        ruleDao.clearUserRules()
        organizationRepository.ensureSeeded()
        Logx.i("Backup", "app metadata cleared")
    }

    companion object {
        const val BACKUP_VERSION = 1
        const val MAX_BACKUP_BYTES = 12L * 1024 * 1024
    }
}
