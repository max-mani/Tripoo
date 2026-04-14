package com.manikandan.tripoo.data.model

data class TripMember(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val isAdmin: Boolean = false,
    /** Enriched from users/{userId} for display (not required on member Firestore docs). */
    val avatarLetter: String? = null,
    val avatarColorHex: String? = null
)
