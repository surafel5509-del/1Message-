package com.onemessages.app.core

import android.util.Log
import com.onemessages.app.BuildConfigCompat
import com.onemessages.app.data.local.DiagnosticsDao
import com.onemessages.app.data.local.DiagnosticEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Local-only diagnostics logger.
 *
 * Privacy rule: message bodies, OTP codes, phone numbers and contact names must never be
 * passed to this object. Callers pass short technical strings only. Everything written in
 * release builds is also scrubbed by [scrub] so an accidental number or code cannot end up
 * in the on-device log.
 */
object Logx {

    private const val TAG = "1Messages"

    /** Diagnostics are debug-only for Logcat, but always kept locally for the storage screen. */
    var persistLocally: Boolean = true

    @Volatile
    private var dao: DiagnosticsDao? = null

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attach(dao: DiagnosticsDao) {
        this.dao = dao
    }

    fun d(tag: String, message: String) = record("D", tag, message, null)

    fun i(tag: String, message: String) = record("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) =
        record("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        record("E", tag, message, error)

    private fun record(level: String, tag: String, message: String, error: Throwable?) {
        val safeTag = tag.take(32)
        val safeMessage = scrub(message)
        if (BuildConfigCompat.isDebug) {
            // Note: Throwable.message can contain provider data, so only the type is logged.
            Log.println(
                when (level) {
                    "E" -> Log.ERROR
                    "W" -> Log.WARN
                    else -> Log.DEBUG
                },
                TAG,
                "[$safeTag] $safeMessage${error?.let { " (${it.javaClass.simpleName})" } ?: ""}"
            )
        }
        if (!persistLocally) return
        val sink = dao ?: return
        ioScope.launch {
            runCatching {
                sink.insert(
                    DiagnosticEntity(
                        level = level,
                        tag = safeTag,
                        message = safeMessage,
                        errorType = error?.javaClass?.simpleName
                    )
                )
            }
        }
    }

    /**
     * Removes digit runs of 5+ (phone numbers, OTP codes) and anything that looks like an
     * email address from a log line.
     */
    fun scrub(raw: String): String {
        val noEmail = raw.replace(EMAIL_REGEX, "[redacted-address]")
        val noNumbers = noEmail.replace(NUMBER_RUN_REGEX, "[redacted-number]")
        return noNumbers.take(400)
    }

    private val NUMBER_RUN_REGEX = Regex("""\+?\d[\d\s\-()]{4,}\d""")
    private val EMAIL_REGEX = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
}
