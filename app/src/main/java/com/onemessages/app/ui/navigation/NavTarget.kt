package com.onemessages.app.ui.navigation

import android.content.Intent
import android.net.Uri

/**
 * Where an external intent wants the app to land.
 *
 * Three entry points matter for a default SMS app:
 *  - `sms:`/`smsto:`/`mms:`/`mmsto:` links from contacts, the dialer or the browser,
 *  - `ACTION_SEND`/`ACTION_SEND_MULTIPLE` "share text with someone",
 *  - our own `onemessages://conversation/<threadId>` links from notifications.
 */
data class NavTarget(
    val threadId: Long? = null,
    val address: String? = null,
    val body: String? = null,
)

object ShareIntentParser {

    private val ADDRESS_SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

    fun fromIntent(intent: Intent?): NavTarget? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            val recipients = intent.getStringArrayExtra(Intent.EXTRA_EMAIL)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
            if (text == null && recipients == null) return null
            return NavTarget(address = recipients, body = text)
        }
        val data = intent.data ?: return null
        val scheme = data.scheme?.lowercase()
        if (scheme == "onemessages") {
            val threadId = data.pathSegments.lastOrNull()?.toLongOrNull()?.takeIf { it > 0 }
            return NavTarget(threadId = threadId, address = data.getQueryParameter("address"))
        }
        if (scheme !in ADDRESS_SCHEMES) return null
        // The number (and optional body) live in the scheme-specific part, e.g.
        // sms:+251911234567?body=hi  or  smsto:1234
        val raw = data.schemeSpecificPart.orEmpty()
        val address = raw.substringBefore('?').trim().takeIf { it.isNotEmpty() }
        val body = raw.substringAfter("body=", "").takeIf { it.isNotBlank() }
        if (address == null) return null
        return NavTarget(address = address, body = body)
    }

    /** Convenience for the Compose test suite and for logging without leaking the number. */
    fun isAddressScheme(uri: Uri?): Boolean = uri?.scheme?.lowercase() in ADDRESS_SCHEMES
}
