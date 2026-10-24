package com.onemessages.app.data.repository

import android.content.Context
import android.text.format.DateFormat
import com.onemessages.app.data.local.DiagnosticEntity
import com.onemessages.app.data.local.DiagnosticsDao
import com.onemessages.app.telephony.SmsRoleManager
import com.onemessages.app.telephony.SubscriptionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsDao: DiagnosticsDao,
    private val roleManager: SmsRoleManager,
    private val subscriptions: SubscriptionRepository,
) {
    val entries: Flow<List<DiagnosticEntity>> = diagnosticsDao.observeRecent()

    data class EnvironmentSnapshot(
        val isDefaultSmsApp: Boolean,
        val simCount: Int,
        val radioState: String,
        val sdkInt: Int,
    )

    suspend fun environment(): EnvironmentSnapshot = withContext(Dispatchers.IO) {
        EnvironmentSnapshot(
            isDefaultSmsApp = roleManager.isDefaultSmsApp(),
            simCount = subscriptions.sims().size,
            radioState = subscriptions.radioState().name,
            sdkInt = android.os.Build.VERSION.SDK_INT,
        )
    }

    suspend fun clear() = withContext(Dispatchers.IO) { diagnosticsDao.clear() }

    suspend fun exportText(): String = withContext(Dispatchers.IO) {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm:ss" else "h:mm:ss a"
        diagnosticsDao.recent().joinToString(separator = "\n") { entry ->
            val time = DateFormat.format(pattern, Date(entry.timestamp))
            val error = entry.errorType?.let { " ($it)" }.orEmpty()
            "$time ${entry.level}/${entry.tag}: ${entry.message}$error"
        }
    }
}
