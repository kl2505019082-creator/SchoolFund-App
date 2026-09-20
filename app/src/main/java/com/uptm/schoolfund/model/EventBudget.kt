package com.uptm.schoolfund.model

/**
 * Represents a school event budget (e.g. Sports Day, class trip),
 * tracked separately from the general class fund.
 */
data class EventBudget(
    val id: Long = 0,
    val name: String,
    val budget: Double,
    val date: String // yyyy-MM-dd
)
