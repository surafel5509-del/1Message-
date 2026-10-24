package com.onemessages.app.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.onemessages.app.core.Logx
import com.onemessages.app.model.ContactIdentity
import com.onemessages.app.model.ContactPhone
import com.onemessages.app.model.DeviceContact
import com.onemessages.app.util.PhoneNumbers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Contacts access with an in-memory index.
 *
 * The index maps normalised numbers to identities so the inbox can resolve names for
 * hundreds of threads without a PhoneLookup query per row. Nothing is written to the
 * contacts provider without an explicit user action.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    private val index = MutableStateFlow<Map<String, ContactIdentity>>(emptyMap())
    val identityIndex: StateFlow<Map<String, ContactIdentity>> = index.asStateFlow()

    private val contactsState = MutableStateFlow<List<DeviceContact>>(emptyList())
    val contacts: StateFlow<List<DeviceContact>> = contactsState.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            index.value = emptyMap()
            contactsState.value = emptyList()
            return@withContext false
        }
        val loaded = loadAllContacts()
        contactsState.value = loaded
        index.value = buildIndex(loaded)
        Logx.d("Contacts", "indexed ${loaded.size} contacts")
        true
    }

    private fun loadAllContacts(): List<DeviceContact> {
        val byContact = LinkedHashMap<Long, MutableContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(projection[0])
                val nameIdx = c.getColumnIndexOrThrow(projection[1])
                val numberIdx = c.getColumnIndexOrThrow(projection[2])
                val typeIdx = c.getColumnIndex(projection[3])
                val labelIdx = c.getColumnIndex(projection[4])
                val photoIdx = c.getColumnIndex(projection[5])
                val starredIdx = c.getColumnIndex(projection[6])
                while (c.moveToNext()) {
                    val contactId = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: continue
                    val number = c.getString(numberIdx) ?: continue
                    if (number.isBlank()) continue
                    val label = labelFor(
                        type = if (typeIdx >= 0) {
                            c.getInt(typeIdx)
                        } else {
                            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                        },
                        custom = if (labelIdx >= 0) c.getString(labelIdx) else null,
                    )
                    val entry = byContact.getOrPut(contactId) {
                        MutableContact(
                            id = contactId,
                            name = name,
                            photoUri = if (photoIdx >= 0) c.getString(photoIdx) else null,
                            starred = starredIdx >= 0 && c.getInt(starredIdx) == 1,
                        )
                    }
                    if (entry.phones.none { PhoneNumbers.sameNumber(it.number, number) }) {
                        entry.phones += ContactPhone(
                            number = number,
                            label = label,
                            normalizedKey = PhoneNumbers.comparisonKey(number),
                        )
                    }
                }
            }
        }.onFailure { Logx.w("Contacts", "contact query failed", it) }
        return byContact.values.map { contact ->
            DeviceContact(
                contactId = contact.id,
                displayName = contact.name,
                photoUri = contact.photoUri,
                phones = contact.phones,
                starred = contact.starred,
            )
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }

    private fun buildIndex(loaded: List<DeviceContact>): Map<String, ContactIdentity> {
        val map = HashMap<String, ContactIdentity>(loaded.size * 2)
        for (contact in loaded) {
            for (phone in contact.phones) {
                map.putIfAbsent(
                    phone.normalizedKey,
                    ContactIdentity(
                        address = phone.number,
                        name = contact.displayName,
                        photoUri = contact.photoUri,
                        contactId = contact.contactId,
                        matchedNumber = phone.number,
                    ),
                )
            }
        }
        return map
    }

    /** Index lookup with a single PhoneLookup fallback for numbers seen after the index. */
    suspend fun lookup(address: String): ContactIdentity = withContext(Dispatchers.IO) {
        val key = PhoneNumbers.comparisonKey(address)
        index.value[key]?.let { return@withContext it }
        if (!hasPermission()) return@withContext ContactIdentity(address, null, null, null)
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        runCatching {
            resolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    ContactIdentity(
                        address = address,
                        name = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)),
                        photoUri = c.getString(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)),
                        contactId = c.getLong(c.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)),
                    )
                } else {
                    null
                }
            }
        }.getOrNull() ?: ContactIdentity(address, null, null, null)
    }

    fun identitiesFor(addresses: Collection<String>): Map<String, ContactIdentity> {
        val current = index.value
        return addresses.associateWith { address ->
            current[PhoneNumbers.comparisonKey(address)]
                ?: ContactIdentity(address, null, null, null)
        }
    }

    fun search(query: String): List<DeviceContact> {
        if (query.isBlank()) return contacts.value.take(200)
        val digits = PhoneNumbers.digitsOnly(query)
        val lower = query.trim().lowercase()
        return contacts.value.filter { contact ->
            contact.displayName.lowercase().contains(lower) ||
                contact.phones.any { phone ->
                    phone.number.contains(query.trim()) ||
                        (digits.isNotEmpty() && PhoneNumbers.digitsOnly(phone.number).contains(digits))
                }
        }.take(100)
    }

    fun contactDetailsIntent(contactId: Long): Intent? {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Opens the platform "add contact" screen pre-filled with [number]. */
    fun addContactIntent(number: String): Intent =
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
            putExtra(
                ContactsContract.Intents.Insert.PHONE_TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            )
        }

    private fun labelFor(type: Int, custom: String?): String? = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Fax"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> custom
        else -> null
    }

    private class MutableContact(
        val id: Long,
        val name: String,
        val photoUri: String?,
        val starred: Boolean,
    ) {
        val phones = mutableListOf<ContactPhone>()
    }
}
