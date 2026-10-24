package com.onemessages.app.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Broadcasts "something changed in the SMS provider" to every active screen.
 *
 * A ContentObserver is cheaper and more reliable than polling, and it also covers messages
 * inserted by other apps or by the platform after process death.
 */
@Singleton
class ProviderChangeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _changes = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes: SharedFlow<Unit> = _changes

    private var observer: ContentObserver? = null

    private val watchedUris: List<Uri> = listOf(
        Telephony.Sms.CONTENT_URI,
        Telephony.Mms.CONTENT_URI,
        Telephony.Threads.CONTENT_URI,
    )

    @Synchronized
    fun start() {
        if (observer != null) return
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                _changes.tryEmit(Unit)
            }
        }
        runCatching {
            watchedUris.forEach { uri -> context.contentResolver.registerContentObserver(uri, true, contentObserver) }
        }.onFailure { com.onemessages.app.core.Logx.w("Provider", "observer registration failed", it) }
        observer = contentObserver
    }

    @Synchronized
    fun stop() {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    /** Used by receivers after they finish a write so the UI updates immediately. */
    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
