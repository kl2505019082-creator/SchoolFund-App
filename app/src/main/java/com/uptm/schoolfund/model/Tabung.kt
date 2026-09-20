package com.uptm.schoolfund.model

/**
 * A named ongoing fund/pot (e.g. "Tabung PIBG", "Tabung Kebajikan") that the teacher creates
 * themselves, separate from the general Class Fund and from budgeted Events. Balance is simply
 * income minus expense recorded against it — no fixed budget cap, unlike an EventBudget.
 */
data class Tabung(
    val id: Long = 0,
    val name: String,
    val subtitle: String = ""
)
