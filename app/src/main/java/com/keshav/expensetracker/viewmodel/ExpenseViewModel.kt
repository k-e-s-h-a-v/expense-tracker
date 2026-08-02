package com.keshav.expensetracker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keshav.expensetracker.model.ExpenseCategory
import com.keshav.expensetracker.model.SmsTransaction
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FilterSnapshot(
    val startDateMs: Long,
    val endDateMs: Long,
    val selectedSenders: Set<String>,
    val selectedMerchants: Set<String>,
    val searchQuery: String = ""
)

class ExpenseViewModel : ViewModel() {
    // Defaults for the "All Transactions" view.
    val defaultDateMs = getFirstDayOfMonth()
    val defaultEndDateMs = getEndOfToday()

    private val _transactions = MutableStateFlow<List<SmsTransaction>>(emptyList())
    val transactions: StateFlow<List<SmsTransaction>> = _transactions.asStateFlow()

    private val _allSenders = MutableStateFlow<Set<String>>(emptySet())
    val allSenders: StateFlow<Set<String>> = _allSenders.asStateFlow()

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent.asStateFlow()

    private val _selectedDateMs = MutableStateFlow(defaultDateMs)
    val selectedDateMs: StateFlow<Long> = _selectedDateMs.asStateFlow()

    private val _selectedEndDateMs = MutableStateFlow(defaultEndDateMs)
    val selectedEndDateMs: StateFlow<Long> = _selectedEndDateMs.asStateFlow()

    private val _hasRollingEndDate = MutableStateFlow(true)
    val hasRollingEndDate: StateFlow<Boolean> = _hasRollingEndDate.asStateFlow()

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val amountRegex = Regex("(?i)(?:rs\\.?|inr)\\s?([\\d,]+\\.?\\d*)")
    private val debitRegex =
            Regex(
                    "(?i)(debited|spent|paid|sent|withdrawn|used at|transaction at|payment to|purchased at)"
            )

    // Improved merchant extraction based on various formats
    private val merchantPatterns =
            listOf(
                    Regex("(?i)used at\\s+(.*?)\\s+for"),
                    Regex("(?i)transaction at\\s+(.*?)\\s+for"),
                    Regex("(?i)paid to\\s+(.*?)\\s+on"),
                    Regex("(?i)sent to\\s+(.*?)\\s+on"),
                    Regex(
                            "(?i);\\s+(.*?)\\s+credited"
                    ), // ICICI case: debited for ...; XXX credited
                    Regex("(?i)(?:to|at)\\s+([^.,\\d][^.,]*(?=(?:on|using|ref|for)))")
            )

    // Load everything
    fun init(context: Context) {
        loadCategories(context)
        resolveRollingEndDate()
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
        val newCat =
                ExpenseCategory(
                        id = id,
                        name = name,
                        startDateMs = _selectedDateMs.value,
                        endDateMs = _selectedEndDateMs.value,
                        selectedSenders = _selectedSenders.value,
                        selectedMerchants = _selectedMerchants.value,
                        searchQuery = _searchQuery.value,
                        hasRollingEndDate = _hasRollingEndDate.value
                )
        saveCategoryToPrefs(context, newCat)

        val currentCats = _categories.value.toMutableList()
        currentCats.add(newCat)
        _categories.value = currentCats
        _activeCategoryId.value = id
    }

    fun updateCategory(context: Context, categoryId: String, newName: String) {
        val currentCat = _categories.value.find { it.id == categoryId } ?: return
        val updatedCat =
                currentCat.copy(
                        name = newName,
                        startDateMs = _selectedDateMs.value,
                        endDateMs = _selectedEndDateMs.value,
                        selectedSenders = _selectedSenders.value,
                        selectedMerchants = _selectedMerchants.value,
                        searchQuery = _searchQuery.value,
                        hasRollingEndDate = _hasRollingEndDate.value
                )

        // Remove old and add new in SharedPreferences
        val prefs = context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)
        val currentStrings =
                prefs.getStringSet("categories", emptySet())?.toMutableSet() ?: mutableSetOf()

        // Find and remove old serialization
        val oldSerialization = currentStrings.find { it.startsWith("$categoryId|") }
        if (oldSerialization != null) {
            currentStrings.remove(oldSerialization)
        }

        currentStrings.add(serializeCategory(updatedCat))
        prefs.edit().putStringSet("categories", currentStrings).apply()

        val updatedCats = _categories.value.map { if (it.id == categoryId) updatedCat else it }
        _categories.value = updatedCats
    }

    fun deleteCategory(context: Context, categoryId: String) {
        val prefs = context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)
        val currentStrings =
                prefs.getStringSet("categories", emptySet())?.toMutableSet() ?: mutableSetOf()

        val toRemove = currentStrings.find { it.startsWith("$categoryId|") }
        if (toRemove != null) {
            currentStrings.remove(toRemove)
            prefs.edit().putStringSet("categories", currentStrings).apply()
        }

        _categories.value = _categories.value.filter { it.id != categoryId }
        if (_activeCategoryId.value == categoryId) {
            selectCategory(context = context, categoryId = null)
        }
    }

    fun duplicateCategory(context: Context, categoryId: String) {
        val currentCat = _categories.value.find { it.id == categoryId } ?: return
        val newId = UUID.randomUUID().toString()
        val duplicatedCat = currentCat.copy(id = newId, name = "${currentCat.name} (Copy)")
        saveCategoryToPrefs(context, duplicatedCat)

        val currentCats = _categories.value.toMutableList()
        currentCats.add(duplicatedCat)
        _categories.value = currentCats
        _activeCategoryId.value = newId
    }

    private fun saveCategoryToPrefs(context: Context, cat: ExpenseCategory) {
        val prefs = context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)
        val currentStrings =
                prefs.getStringSet("categories", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentStrings.add(serializeCategory(cat))
        prefs.edit().putStringSet("categories", currentStrings).apply()
    }

    fun selectCategory(context: Context, categoryId: String?) {
        _activeCategoryId.value = categoryId
        if (categoryId == null) {
            clearFilters()
            _hasRollingEndDate.value = true
            _selectedDateMs.value = getFirstDayOfMonth()
            resolveRollingEndDate()
        } else {
            val cat = _categories.value.find { it.id == categoryId }
            cat?.let {
                _selectedDateMs.value = it.startDateMs
                _selectedEndDateMs.value = it.endDateMs
                _hasRollingEndDate.value = it.hasRollingEndDate
                resolveRollingEndDate()
                _selectedSenders.value = it.selectedSenders
                _selectedMerchants.value = it.selectedMerchants
                _searchQuery.value = it.searchQuery
            }
        }
        loadMessages(context)
    }

    private fun serializeCategory(cat: ExpenseCategory): String {
        val senders = cat.selectedSenders.joinToString(",")
        val merchants = cat.selectedMerchants.joinToString(",")
        return "${cat.id}|${cat.name}|${cat.startDateMs}|${cat.endDateMs}|$senders|$merchants|${cat.searchQuery}|${cat.hasRollingEndDate}"
    }

    private fun deserializeCategory(str: String): ExpenseCategory? {
        return try {
            val parts = str.split("|")
            if (parts.size < 5) return null
            val startDateMs = parts[2].toLong()
            val endDateMs = if (parts.size >= 6) parts[3].toLong() else startDateMs
            val sendersValue = if (parts.size >= 6) parts[4] else parts[3]
            val merchantsValue = if (parts.size >= 6) parts[5] else parts[4]

            ExpenseCategory(
                    id = parts[0],
                    name = parts[1],
                    startDateMs = startDateMs,
                    endDateMs = endDateMs,
                    selectedSenders =
                            if (sendersValue.isEmpty()) emptySet() else sendersValue.split(",").toSet(),
                    selectedMerchants =
                            if (merchantsValue.isEmpty()) emptySet() else merchantsValue.split(",").toSet(),
                    searchQuery = parts.getOrElse(6) { "" },
                    hasRollingEndDate = parts.getOrElse(7) { "false" }.toBoolean()
            )
        } catch (e: Exception) {
            null
        }
    }

    // Load messages based on the selected date
    fun loadMessages(context: Context) {
        viewModelScope.launch {
            val list = fetchAndParseSms(context, _selectedDateMs.value, _selectedEndDateMs.value)
            _transactions.value = list
            _totalSpent.value = list.filter { it.isDebit }.sumOf { it.amount }

            // Also fetch all unique senders from the same period
            _allSenders.value = fetchAllSenders(context, _selectedDateMs.value, _selectedEndDateMs.value)
        }
    }

    fun updateStartDate(context: Context, dateMs: Long) {
        _selectedDateMs.value = startOfDay(dateMs)
        loadMessages(context) // Reload when date changes
    }

    fun updateEndDate(context: Context, dateMs: Long) {
        _selectedEndDateMs.value = endOfDay(dateMs)
        _hasRollingEndDate.value = false
        loadMessages(context) // Reload when date changes
    }

    fun setRollingEndDate(context: Context, enabled: Boolean) {
        _hasRollingEndDate.value = enabled
        if (enabled) resolveRollingEndDate()
        loadMessages(context)
    }

    fun refreshRollingEndDate(context: Context) {
        if (_hasRollingEndDate.value && _selectedEndDateMs.value != getEndOfToday()) {
            resolveRollingEndDate()
            loadMessages(context)
        }
    }

    fun updateDateRange(startDateMs: Long, endDateMs: Long) {
        _selectedDateMs.value = startDateMs
        _selectedEndDateMs.value = endDateMs
        _hasRollingEndDate.value = false
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Save a merchant name for a specific transaction ID
    fun saveMerchantMapping(context: Context, transactionId: String, merchantName: String) {
        val prefs = context.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(transactionId, merchantName).apply()
        loadMessages(context) // Reload to apply the new name
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
        _searchQuery.value = ""
        _hasRollingEndDate.value = true
        _selectedDateMs.value = getFirstDayOfMonth()
        resolveRollingEndDate()
    }

    fun buildFilterSnapshot(): FilterSnapshot {
        return FilterSnapshot(
            startDateMs = _selectedDateMs.value,
            endDateMs = _selectedEndDateMs.value,
            selectedSenders = _selectedSenders.value,
            selectedMerchants = _selectedMerchants.value,
            searchQuery = _searchQuery.value
        )
    }

    fun applyFilterSnapshot(snapshot: FilterSnapshot) {
        _selectedDateMs.value = snapshot.startDateMs
        _selectedEndDateMs.value = snapshot.endDateMs
        _selectedSenders.value = snapshot.selectedSenders.toSet()
        _selectedMerchants.value = snapshot.selectedMerchants.toSet()
        _searchQuery.value = snapshot.searchQuery
    }

    fun exportFilters(context: Context) {
        val prefs = context.getSharedPreferences("expense_filter_snapshot", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("current", serializeFilterSnapshot(buildFilterSnapshot()))
            .apply()
    }

    fun exportFiltersToJson(context: Context, uri: Uri) {
        val snapshot = buildFilterSnapshot()
        val savedCategories = _categories.value.toList()
        val activeId = _activeCategoryId.value
        val rollingEndDate = _hasRollingEndDate.value
        viewModelScope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                val categoriesJson = JSONArray().apply {
                    savedCategories.forEach { put(it.toJson()) }
                }
                writer.write(
                        JSONObject()
                                .put("format", "expense-tracker-filters")
                                .put("version", 2)
                                .put("activeFilter", snapshot.toJson(rollingEndDate))
                                .put("activeCategoryId", activeId)
                                .put("categories", categoriesJson)
                                .toString(2)
                )
            }
        }
    }

    fun importFiltersFromJson(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: return@launch
                val filter = JSONObject(json)
                require(filter.getString("format") == "expense-tracker-filters")
                val activeFilter = filter.optJSONObject("activeFilter") ?: filter
                val snapshot = activeFilter.toFilterSnapshot()
                _hasRollingEndDate.value = activeFilter.optBoolean("rollingEndDate", false)
                val importedCategories = filter.categories()
                if (importedCategories.isNotEmpty()) mergeImportedCategories(context, importedCategories)
                _activeCategoryId.value = filter.optString("activeCategoryId").takeIf { id ->
                    _categories.value.any { it.id == id }
                }
                applyFilterSnapshot(snapshot)
                resolveRollingEndDate()
                loadMessages(context)
            }
        }
    }

    fun importFilters(context: Context) {
        val prefs = context.getSharedPreferences("expense_filter_snapshot", Context.MODE_PRIVATE)
        val snapshot = prefs.getString("current", null)?.let(::parseFilterSnapshot) ?: return
        applyFilterSnapshot(snapshot)
    }

    companion object {
        fun parseFilterSnapshot(raw: String): FilterSnapshot {
            val parts = raw.split("|")
            require(parts.size >= 4)
            return FilterSnapshot(
                startDateMs = parts[0].toLong(),
                endDateMs = parts[1].toLong(),
                selectedSenders = if (parts[2].isEmpty()) emptySet() else parts[2].split(",").toSet(),
                selectedMerchants = if (parts[3].isEmpty()) emptySet() else parts[3].split(",").toSet(),
                searchQuery = parts.getOrElse(4) { "" }
            )
        }

        fun matchesSearchQuery(query: String, vararg searchableValues: String?): Boolean {
            if (query.isBlank()) return true
            val searchableText = searchableValues.filterNotNull().joinToString(" ")
            return query
                    .trim()
                    .split(Regex("\\s+(?i:OR)\\s+"))
                    .any { andGroup ->
                        andGroup
                                .split(Regex("\\s+(?i:AND)\\s+"))
                                .filter { it.isNotBlank() }
                                .all { keyword ->
                                    searchableText.contains(keyword.trim(), ignoreCase = true)
                                }
                    }
        }
    }

    private fun serializeFilterSnapshot(snapshot: FilterSnapshot): String {
        val senders = snapshot.selectedSenders.joinToString(",")
        val merchants = snapshot.selectedMerchants.joinToString(",")
        return "${snapshot.startDateMs}|${snapshot.endDateMs}|$senders|$merchants|${snapshot.searchQuery}"
    }

    private fun JSONObject.stringSet(key: String): Set<String> {
        val values = optJSONArray(key) ?: return emptySet()
        return buildSet { for (index in 0 until values.length()) add(values.getString(index)) }
    }

    private fun FilterSnapshot.toJson(rollingEndDate: Boolean) =
            JSONObject()
                    .put("startDateMs", startDateMs)
                    .put("endDateMs", endDateMs)
                    .put("senders", JSONArray(selectedSenders.toList()))
                    .put("merchants", JSONArray(selectedMerchants.toList()))
                    .put("searchQuery", searchQuery)
                    .put("rollingEndDate", rollingEndDate)

    private fun ExpenseCategory.toJson() =
            JSONObject()
                    .put("id", id)
                    .put("name", name)
                    .put("startDateMs", startDateMs)
                    .put("endDateMs", endDateMs)
                    .put("senders", JSONArray(selectedSenders.toList()))
                    .put("merchants", JSONArray(selectedMerchants.toList()))
                    .put("searchQuery", searchQuery)
                    .put("rollingEndDate", hasRollingEndDate)

    private fun JSONObject.toFilterSnapshot() = FilterSnapshot(
            startDateMs = getLong("startDateMs"),
            endDateMs = getLong("endDateMs"),
            selectedSenders = stringSet("senders"),
            selectedMerchants = stringSet("merchants"),
            searchQuery = optString("searchQuery")
    )

    private fun JSONObject.categories(): List<ExpenseCategory> {
        val values = optJSONArray("categories") ?: return emptyList()
        return buildList {
            for (index in 0 until values.length()) {
                val category = values.optJSONObject(index) ?: continue
                add(
                        ExpenseCategory(
                                id = category.getString("id"),
                                name = category.getString("name"),
                                startDateMs = category.getLong("startDateMs"),
                                endDateMs = category.getLong("endDateMs"),
                                selectedSenders = category.stringSet("senders"),
                                selectedMerchants = category.stringSet("merchants"),
                                searchQuery = category.optString("searchQuery"),
                                hasRollingEndDate = category.optBoolean("rollingEndDate", false)
                        )
                )
            }
        }
    }

    private fun mergeImportedCategories(context: Context, imported: List<ExpenseCategory>) {
        val merged = LinkedHashMap<String, ExpenseCategory>()
        _categories.value.forEach { merged[it.id] = it }
        imported.forEach { merged[it.id] = it }
        _categories.value = merged.values.toList()
        context.getSharedPreferences("expense_categories", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("categories", _categories.value.mapTo(mutableSetOf(), ::serializeCategory))
                .apply()
    }

    private fun resolveRollingEndDate() {
        if (_hasRollingEndDate.value) _selectedEndDateMs.value = getEndOfToday()
    }

    private suspend fun fetchAllSenders(
        context: Context,
        sinceMs: Long,
        untilMs: Long
    ): Set<String> =
            withContext(Dispatchers.IO) {
                val senders = mutableSetOf<String>()
                val cursor =
                        context.contentResolver.query(
                                Uri.parse("content://sms/inbox"),
                                arrayOf("address"),
                                "date >= ? AND date <= ?",
                                arrayOf(sinceMs.toString(), untilMs.toString()),
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

    private suspend fun fetchAndParseSms(
        context: Context,
        sinceMs: Long,
        untilMs: Long
    ): List<SmsTransaction> =
            withContext(Dispatchers.IO) {
                val transactions = mutableListOf<SmsTransaction>()
                val prefs = context.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)

                val cursor =
                        context.contentResolver.query(
                                Uri.parse("content://sms/inbox"),
                                arrayOf("_id", "address", "date", "body"),
                                "date >= ? AND date <= ?",
                                arrayOf(sinceMs.toString(), untilMs.toString()),
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

                                // Try extract merchant from body using multiple patterns
                                var extractedMerchant: String? = null
                                for (regex in merchantPatterns) {
                                    val match = regex.find(body)
                                    if (match != null) {
                                        extractedMerchant = match.groupValues.getOrNull(1)?.trim()
                                        if (!extractedMerchant.isNullOrBlank()) break
                                    }
                                }

                                val txId = it.getString(idIdx)
                                val savedMerchant = prefs.getString(txId, null)

                                transactions.add(
                                        SmsTransaction(
                                                id = txId,
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

    private fun getEndOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    private fun startOfDay(dateMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun endOfDay(dateMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }
}
