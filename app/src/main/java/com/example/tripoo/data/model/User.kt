package com.example.tripoo.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val tripIds: List<String> = emptyList(),
    val lastActiveTripId: String? = null
)
