package com.manikandan.tripoo.notifications

/**
 * `type` values written to [trips/{id}/fanoutNotifications].
 * There is no server-side whitelist.
 */
object FanoutTypes {
    const val MEMBER_JOINED = "member_joined"
    const val MEMBER_LEFT = "member_left"
    const val MEMBER_REMOVED = "member_removed"
    const val ADMIN_TRANSFER = "admin_transfer"
    const val TRIP_EDITED = "trip_edited"
    const val TRIP_DELETED = "trip_deleted"

    const val EXPENSE_ADDED = "expense_added"
    const val EXPENSE_EDITED = "expense_edited"
    const val EXPENSE_DELETED = "expense_deleted"
    const val EXPENSE_SETTLED = "expense_settled"

    const val TASK_ADDED = "task_added"
    const val TASK_EDITED = "task_edited"
    const val TASK_DELETED = "task_deleted"

    const val SETTLEMENT_ADDED = "settlement_added"
    const val POLL_CREATED = "poll_created"
    const val POLL_CLOSED = "poll_closed"
    const val ITINERARY_UPDATED = "itinerary_updated"
}
