package com.keshav.expensetracker.model

data class ExpenseCategory(
        val id: String,
        val name: String,
        val startDateMs: Long,
        val endDateMs: Long = startDateMs,
        val selectedSenders: Set<String>,
        val selectedMerchants: Set<String>,
        val searchQuery: String = "",
        val hasRollingEndDate: Boolean = false
)
