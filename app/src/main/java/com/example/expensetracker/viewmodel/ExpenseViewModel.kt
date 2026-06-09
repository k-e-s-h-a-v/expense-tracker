package com.example.expensetracker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.model.ExpenseCategory
import com.example.expensetracker.model.SmsTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ExpenseViewModel : ViewModel() {
    private val _transactions = MutableStateFlow<List<SmsTransaction>>(emptyList())
    val transactions: StateFlow<List<SmsTransaction>> = _transactions.asStateFlow()

    private val _allSenders = MutableStateFlow<Set<String>>(emptySet())
    val allSenders: StateFlow<Set<String>> = _allSenders.asStateFlow()

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent.asStateFlow()

    private val _selectedDateMs = MutableStateFlow(getFirstDayOfMonth())
    val selectedDateMs: StateFlow<Long> = _selectedDateMs.asStateFlow()

    // State to track selected filter senders (SMS address)
    private val _selectedSenders = MutableStateFlow<Set<String>>(emptySet())
    val selectedSenders: StateFlow<Set<String>> = _selectedSenders.asStateFlow()

    // State to track selected filter merchants
    private val _selectedMerchants = MutableStateFlow<Set<String>>(emptySet())
    val selectedMerchants: StateFlow<Set<String>> = _selectedMerchants.asStateFlow()

    private val _categories = MutableStateFlow<List<ExpenseCategory>>(emptyList())
    val categories: StateFlow<List<ExpenseCategory>> = _categories.asStateFlow()

    private val _activeCategoryId = MutableStateFlow<String?>(null)
    val activeCategoryId: StateFlow<String?> = _activeCategoryId.asStateFlow()

    private val amountRegex = Regex("(?i)(?:rs\\.?|inr)\\s?([\\d,]+\\.?\\d*)")
    private val debitRegex = Regex("(?i)(debited|spent|paid|sent|withdrawn)")
    private val upiMerchantRegex = Regex("(?i)(?:to|at)\\s+([^.,\\d][^.,]*(?=(?:on|using|ref)))")

    // Load everything
    fun init(context: Context) {
        loadCategories(context)
        loadMessages(context)
    }

    private fun loadCategories(context: Context) {
        val prefs = context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)
        val categoryStrings = prefs.getStringSet("categories", emptySet()) ?: emptySet()
        val cats = categoryStrings.mapNotNull { deserializeCategory(it) }
        _categories.value = cats
    }

    fun saveCategory(context: Context, name: String) {
        val id = UUID.randomUUID().toString()
        val newCat = ExpenseCategory(
            id = id,
            name = name,
            startDateMs = _selectedDateMs.value,
            selectedSenders = _selectedSenders.value,
            selectedMerchants = _selectedMerchants.value
        )
        val prefs = context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)
        val currentStrings = prefs.getStringSet("categories", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentStrings.add(serializeCategory(newCat))
        prefs.edit().putStringSet("categories", currentStrings).apply()
        
        val currentCats = _categories.value.toMutableList()
        currentCats.add(newCat)
        _categories.value = currentCats
        _activeCategoryId.value = id
    }

    fun selectCategory(categoryId: String?) {
        _activeCategoryId.value = categoryId
        if (categoryId == null) {
            clearFilters()
            _selectedDateMs.value = getFirstDayOfMonth() // Or keep current? User said category is a filter.
        } else {
            val cat = _categories.value.find { it.id == categoryId }
            cat?.let {
                _selectedDateMs.value = it.startDateMs
                _selectedSenders.value = it.selectedSenders
                _selectedMerchants.value = it.selectedMerchants
            }
        }
    }

    private fun serializeCategory(cat: ExpenseCategory): String {
        val senders = cat.selectedSenders.joinToString(",")
        val merchants = cat.selectedMerchants.joinToString(",")
        return "${cat.id}|${cat.name}|${cat.startDateMs}|$senders|$merchants"
    }

    private fun deserializeCategory(str: String): ExpenseCategory? {
        return try {
            val parts = str.split("|")
            if (parts.size < 5) return null
            ExpenseCategory(
                id = parts[0],
                name = parts[1],
                startDateMs = parts[2].toLong(),
                selectedSenders = if (parts[3].isEmpty()) emptySet() else parts[3].split(",").toSet(),
                selectedMerchants = if (parts[4].isEmpty()) emptySet() else parts[4].split(",").toSet()
            )
        } catch (e: Exception) {
            null
        }
    }

    // Load messages based on the selected date
    fun loadMessages(context: Context) {
        viewModelScope.launch {
            val list = fetchAndParseSms(context, _selectedDateMs.value)
            _transactions.value = list
            _totalSpent.value = list.filter { it.isDebit }.sumOf { it.amount }
            
            // Also fetch all unique senders from the same period
            _allSenders.value = fetchAllSenders(context, _selectedDateMs.value)
        }
    }

    fun updateStartDate(context: Context, dateMs: Long) {
        _selectedDateMs.value = dateMs
        loadMessages(context) // Reload when date changes
    }

    // Save a merchant name for a specific sender ID (User mapping)
    fun saveMerchantMapping(context: Context, sender: String, merchantName: String) {
        val prefs = context.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(sender, merchantName).apply()
        loadMessages(context) // Reload to apply the new names
    }

    // Toggle selected filter sender
    fun toggleSenderFilter(sender: String) {
        val current = _selectedSenders.value.toMutableSet()
        if (current.contains(sender)) {
            current.remove(sender)
        } else {
            current.add(sender)
        }
        _selectedSenders.value = current
    }

    // Toggle selected filter merchant
    fun toggleMerchantFilter(merchant: String) {
        val current = _selectedMerchants.value.toMutableSet()
        if (current.contains(merchant)) {
            current.remove(merchant)
        } else {
            current.add(merchant)
        }
        _selectedMerchants.value = current
    }

    // Clear all filters
    fun clearFilters() {
        _selectedSenders.value = emptySet()
        _selectedMerchants.value = emptySet()
    }

    private suspend fun fetchAllSenders(context: Context, sinceMs: Long): Set<String> = withContext(Dispatchers.IO) {
        val senders = mutableSetOf<String>()
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address"),
            "date >= ?",
            arrayOf(sinceMs.toString()),
            null
        )
        cursor?.use {
            val addrIdx = it.getColumnIndex("address")
            while (it.moveToNext()) {
                senders.add(it.getString(addrIdx) ?: "Unknown")
            }
        }
        return@withContext senders
    }

    private suspend fun fetchAndParseSms(context: Context, sinceMs: Long): List<SmsTransaction> = withContext(Dispatchers.IO) {
        val transactions = mutableListOf<SmsTransaction>()
        val prefs = context.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)

        val cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("_id", "address", "date", "body"),
            "date >= ?",
            arrayOf(sinceMs.toString()),
            "date DESC"
        )

        cursor?.use {
            val idIdx = it.getColumnIndex("_id")
            val addrIdx = it.getColumnIndex("address")
            val dateIdx = it.getColumnIndex("date")
            val bodyIdx = it.getColumnIndex("body")

            while (it.moveToNext()) {
                val body = it.getString(bodyIdx) ?: ""
                
                // Only process if it looks like a debit transaction
                if (debitRegex.containsMatchIn(body)) {
                    val amountMatch = amountRegex.find(body)
                    if (amountMatch != null) {
                        val amountStr = amountMatch.groupValues[1].replace(",", "")
                        val amount = amountStr.toDoubleOrNull() ?: 0.0
                        
                        val sender = it.getString(addrIdx) ?: "Unknown"
                        
                        // Try extract merchant from body
                        val extractedMerchant = upiMerchantRegex.find(body)?.groupValues?.get(1)?.trim()
                        val savedMerchant = prefs.getString(sender, null)

                        transactions.add(
                            SmsTransaction(
                                id = it.getString(idIdx),
                                sender = sender,
                                body = body,
                                dateMs = it.getLong(dateIdx),
                                amount = amount,
                                isDebit = true,
                                merchant = savedMerchant ?: extractedMerchant
                            )
                        )
                    }
                }
            }
        }
        return@withContext transactions
    }

    private fun getFirstDayOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
}
