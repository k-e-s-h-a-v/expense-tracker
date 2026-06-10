package com.example.expensetracker.model

data class SmsTransaction(
    val id: String,
    val sender: String, // The SMS address (e.g., AB-AIRINF)
    val body: String,
    val dateMs: Long,
    val amount: Double,
    val isDebit: Boolean,
    var merchant: String? = null // Extracted or mapped merchant (e.g., Amazon, Yarra Mohan Rao)
)
