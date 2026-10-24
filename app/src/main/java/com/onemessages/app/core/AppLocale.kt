package com.onemessages.app.core

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language selection without AppCompat.
 *
 * The chosen tag is mirrored into a tiny SharedPreferences file so [MainActivity.attachBaseContext]
 * can wrap the base context synchronously, before any resource is resolved. That works on every
 * supported API level, unlike `LocaleManager` (33+) or `AppCompatDelegate` (which needs an
 * AppCompat theme).
 */
object AppLocale {

    const val SYSTEM = "system"

    private const val PREFS = "onemessages_locale"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    private val SUPPORTED = setOf("en", "am")

    fun normalize(tag: String?): String {
        val cleaned = tag?.trim()?.lowercase().orEmpty()
        if (cleaned.isEmpty() || cleaned == SYSTEM) return SYSTEM
        val language = cleaned.substringBefore('-')
        return if (language in SUPPORTED) language else SYSTEM
    }

    fun save(context: Context, tag: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, normalize(tag))
            .apply()
    }

    fun savedTag(context: Context): String =
        normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE_TAG, null))

    /** Wraps [base] with the saved locale, or returns it untouched for "system". */
    fun wrap(base: Context): Context {
        val tag = savedTag(base)
        if (tag == SYSTEM) return base
        val locale = runCatching { Locale.forLanguageTag(tag) }.getOrNull() ?: return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return runCatching { base.createConfigurationContext(configuration) }.getOrDefault(base)
    }
}
