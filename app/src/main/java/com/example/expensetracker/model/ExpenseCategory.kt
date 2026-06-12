package com.keshav.expensetracker.model

data class ExpenseCategory(
        val id: String,
        val name: String,
        val startDateMs: Long,
        val selectedSenders: Set<String>,
        val selectedMerchants: Set<String>
)
