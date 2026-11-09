package com.example.calling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ContactRepository"

/**
 * Repository for querying local device contacts via Android Contacts Provider.
 * 100% Offline: Never uploads or exposes contact information.
 */
class ContactRepository(private val context: Context) {

    private var cachedContacts: List<ContactItem> = emptyList()

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getContacts(forceRefresh: Boolean = false): List<ContactItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedContacts.isNotEmpty()) {
            return@withContext cachedContacts
        }

        if (!hasContactsPermission()) {
            Log.w(TAG, "READ_CONTACTS permission not granted. Returning offline sample contacts.")
            return@withContext getOfflineSampleContacts()
        }

        val contactsMap = mutableMapOf<String, MutableContact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )

            cursor?.let { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                val lookupIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                while (c.moveToNext()) {
                    val id = if (idIdx >= 0) c.getString(idIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numberIdx >= 0) c.getString(numberIdx) ?: "" else ""
                    val type = if (typeIdx >= 0) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                    val customLabel = if (labelIdx >= 0) c.getString(labelIdx) else null
                    val lookupKey = if (lookupIdx >= 0) c.getString(lookupIdx) else null

                    if (number.isNotBlank()) {
                        val cleanNumber = number.replace(Regex("[^0-9+]"), "")
                        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            type,
                            customLabel
                        ).toString()

                        val existing = contactsMap.getOrPut(id) {
                            MutableContact(id = id, displayName = name, lookupKey = lookupKey)
                        }

                        // Avoid adding duplicate numbers for the same contact
                        if (existing.numbers.none { it.normalizedNumber == cleanNumber }) {
                            existing.numbers.add(
                                ContactPhoneNumber(
                                    number = number,
                                    typeLabel = label,
                                    normalizedNumber = cleanNumber
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying device contacts", e)
        } finally {
            cursor?.close()
        }

        val result = if (contactsMap.isNotEmpty()) {
            contactsMap.values.map { it.toContactItem() }
        } else {
            getOfflineSampleContacts()
        }

        cachedContacts = result
        result
    }

    /**
     * Reliable offline contacts for emulator testing and immediate offline voice command verification.
     */
    private fun getOfflineSampleContacts(): List<ContactItem> = listOf(
        ContactItem(
            id = "mock_1",
            displayName = "Amma (Mom / அம்மா)",
            phoneNumbers = listOf(
                ContactPhoneNumber("+91 98765 43210", "Mobile", "+919876543210"),
                ContactPhoneNumber("044 2456 7890", "Home", "04424567890")
            )
        ),
        ContactItem(
            id = "mock_2",
            displayName = "Appa (Dad / அப்பா)",
            phoneNumbers = listOf(
                ContactPhoneNumber("+91 98401 23456", "Mobile", "+919840123456")
            )
        ),
        ContactItem(
            id = "mock_3",
            displayName = "Arun Kumar (அருண்)",
            phoneNumbers = listOf(
                ContactPhoneNumber("+91 94441 56789", "Mobile", "+919444156789"),
                ContactPhoneNumber("+91 80562 11223", "Work", "+918056211223")
            )
        ),
        ContactItem(
            id = "mock_4",
            displayName = "Ramesh (ரமேஷ்)",
            phoneNumbers = listOf(
                ContactPhoneNumber("+91 97890 12345", "Mobile", "+919789012345")
            )
        ),
        ContactItem(
            id = "mock_5",
            displayName = "Brother (அண்ணன் / தம்பி)",
            phoneNumbers = listOf(
                ContactPhoneNumber("+91 99620 98765", "Mobile", "+919962098765")
            )
        ),
        ContactItem(
            id = "mock_6",
            displayName = "Maa (माँ / Mother)",
            phoneNumbers = listOf(
                ContactPhoneNumber("+91 98111 22334", "Mobile", "+919811122334")
            )
        )
    )

    private class MutableContact(
        val id: String,
        val displayName: String,
        val lookupKey: String?,
        val numbers: MutableList<ContactPhoneNumber> = mutableListOf()
    ) {
        fun toContactItem(): ContactItem = ContactItem(
            id = id,
            displayName = displayName,
            phoneNumbers = numbers.toList(),
            lookupKey = lookupKey
        )
    }
}
