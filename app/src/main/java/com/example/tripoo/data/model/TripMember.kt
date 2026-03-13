package com.example.tripoo.data.model

data class TripMember(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val isAdmin: Boolean = false
)
