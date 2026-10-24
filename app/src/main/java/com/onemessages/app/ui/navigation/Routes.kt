package com.onemessages.app.ui.navigation

import android.net.Uri

object Routes {
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val STARRED = "starred"
    const val SETTINGS = "settings"
    const val ARCHIVED = "archived"
    const val SEARCH = "search"

    const val NEW_MESSAGE = "newMessage?address={address}&body={body}"
    const val CONVERSATION = "conversation/{threadId}?address={address}"

    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_MESSAGING = "settings/messaging"
    const val SETTINGS_ORGANIZATION = "settings/organization"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_STORAGE = "settings/storage"
    const val SETTINGS_ABOUT = "settings/about"

    val topLevel = setOf(CHATS, CONTACTS, STARRED, SETTINGS)

    fun conversation(threadId: Long, address: String): String =
        "conversation/$threadId?address=${Uri.encode(address)}"

    fun newMessage(address: String? = null, body: String? = null): String {
        val encodedAddress = Uri.encode(address.orEmpty())
        val encodedBody = Uri.encode(body.orEmpty())
        return "newMessage?address=$encodedAddress&body=$encodedBody"
    }
}
