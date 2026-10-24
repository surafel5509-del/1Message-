package com.onemessages.app.model

data class ContactPhone(
    val number: String,
    val label: String?, // "Mobile", "Work" etc. as provided by the platform
    val normalizedKey: String,
)

data class DeviceContact(
    val contactId: Long,
    val displayName: String,
    val photoUri: String?,
    val phones: List<ContactPhone>,
    val starred: Boolean = false,
) {
    val primaryPhone: String? get() = phones.firstOrNull()?.number
}

/** Best-known identity for an address, used by the inbox and notification engine. */
data class ContactIdentity(
    val address: String,
    val name: String?,
    val photoUri: String?,
    val contactId: Long?,
    val matchedNumber: String? = null,
) {
    val hasName: Boolean get() = !name.isNullOrBlank()
}
