package com.uptm.schoolfund.model

/**
 * Represents a single income or expense transaction.
 * Belongs to exactly ONE of: the general class fund (eventId and tabungId both null),
 * a specific event budget (eventId set), or a named Tabung/fund (tabungId set).
 */
data class TransactionItem(
    val id: Long = 0,
    val type: String,          // "income" or "expense"
    val amount: Double,
    val description: String,
    val date: String,          // yyyy-MM-dd
    val receiptPath: String?,  // local file path to attached receipt photo, or null
    val eventId: Long?,
    val tabungId: Long? = null
)
