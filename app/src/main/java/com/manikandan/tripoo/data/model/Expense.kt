package com.manikandan.tripoo.data.model

data class Expense(
    val id: String = "",
    /** User id of the member who created this expense (Firestore security). */
    val createdBy: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val category: String = "other",
    val paidBy: String = "",
    val splitWith: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    // Named 'settled' (not 'isSettled') so Firebase Java reflection uses field name 'settled'
    // (boolean getters starting with 'is' are stripped to their suffix by Java BeanInfo).
    // Must be var so Firebase can set it via the generated setter when deserializing.
    // Legacy: new UI settles via trips/{id}/settlements; the ledger ignores this flag.
    var settled: Boolean = false
)
