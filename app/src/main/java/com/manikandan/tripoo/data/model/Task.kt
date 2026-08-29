package com.manikandan.tripoo.data.model

data class Task(
    val id: String = "",
    /** User id of the member who created this task (Firestore security). */
    val createdBy: String = "",
    val title: String = "",
    val category: String = "general",
    val assignedTo: String = "everyone",
    val completed: Boolean = false,
    val dueDate: Long? = null,
    val priority: String = "medium",
    val notes: String? = null,
    /** Set by Cloud Functions after a deadline notification is sent. */
    val deadlineNotified: Boolean = false
) {
    /** Leader, or the specific assignee — not "everyone". */
    fun canToggleCompletion(uid: String, isLeader: Boolean): Boolean {
        if (uid.isBlank()) return false
        if (isLeader) return true
        val a = assignedTo
        return a.isNotBlank() && !a.equals("everyone", ignoreCase = true) && a == uid
    }
    companion object {
        const val CATEGORY_BOOKING = "booking"
        const val CATEGORY_PACKING = "packing"
        const val CATEGORY_GENERAL = "general"
    }
}
