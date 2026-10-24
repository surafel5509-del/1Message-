package com.onemessages.app.data.repository

import android.content.Context
import android.text.format.Formatter
import com.onemessages.app.core.Logx
import com.onemessages.app.data.local.DiagnosticsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageUsage(
    val databaseBytes: Long,
    val preferencesBytes: Long,
    val cacheBytes: Long,
) {
    val totalBytes: Long get() = databaseBytes + preferencesBytes + cacheBytes
}

@Singleton
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsDao: DiagnosticsDao,
) {

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val db = context.getDatabasePath("onemessages.db")
        val prefs = File(context.filesDir, "datastore/settings.preferences_pb")
        StorageUsage(
            databaseBytes = (db?.length() ?: 0L) + File(db?.path + "-wal").length(),
            preferencesBytes = prefs.length(),
            cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
        )
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
            true
        }.onFailure { Logx.w("Storage", "cache clear failed", it) }.getOrDefault(false)
    }

    suspend fun clearDiagnostics() = withContext(Dispatchers.IO) { diagnosticsDao.clear() }

    fun format(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)
}
