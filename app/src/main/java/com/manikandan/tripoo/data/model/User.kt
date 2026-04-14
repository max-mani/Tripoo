package com.manikandan.tripoo.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    /** Optional phone stored in Firestore (display / contact). */
    val phoneNumber: String? = null,
    val preferredLanguage: String? = null,
    val preferredCurrency: String? = null,
    val photoUrl: String? = null,
    val tripIds: List<String> = emptyList(),
    val lastActiveTripId: String? = null,
    /** Single letter shown when there is no profile photo (persisted). */
    val avatarLetter: String? = null,
    /** Fill color (#RRGGBB) for initials avatar when there is no photo (persisted). */
    val avatarColorHex: String? = null
)
