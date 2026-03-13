package com.manikandan.tripoo.data.model

sealed class LeaveTripResult {
    object Success : LeaveTripResult()
    data class MustTransferAdmin(val otherMembers: List<TripMember>) : LeaveTripResult()
    object LastMember : LeaveTripResult()
}

