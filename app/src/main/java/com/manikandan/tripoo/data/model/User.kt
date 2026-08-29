package com.manikandan.tripoo.data.model

data class RecentCollaborator(
    val uid: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val lastSeenAt: Long = 0L
)

data class User @JvmOverloads constructor(
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
    val avatarColorHex: String? = null,
    /** People this user has been on a trip/outing with. Owned-doc only; never written onto someone else. */
    val recentCollaborators: List<RecentCollaborator> = emptyList()
)
