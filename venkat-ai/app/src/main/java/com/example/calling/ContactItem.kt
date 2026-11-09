package com.example.calling

data class ContactPhoneNumber(
    val number: String,
    val typeLabel: String,
    val normalizedNumber: String
)

data class ContactItem(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<ContactPhoneNumber>,
    val lookupKey: String? = null,
    val matchScore: Float = 1.0f
) {
    val primaryNumber: String
        get() = phoneNumbers.firstOrNull()?.number ?: ""
}
