package com.example.expensetracker

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// --- DATA CLASSES ---

data class SmsTransaction(
    val id: String,
    val sender: String,
    val body: String,
    val dateMs: Long,
    val amount: Double,
    val isDebit: Boolean,
    var merchantName: String? = null // This will be filled based on user mapping
)

// --- VIEW MODEL ---

class ExpenseViewModel : ViewModel() {
    private val _transactions = MutableStateFlow<List<SmsTransaction>>(emptyList())
    val transactions: StateFlow<List<SmsTransaction>> = _transactions.asStateFlow()

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent.asStateFlow()

    private val _selectedDateMs = MutableStateFlow(getFirstDayOfMonth())
    val selectedDateMs: StateFlow<Long> = _selectedDateMs.asStateFlow()

    private val amountRegex = Regex("(?i)(?:rs\\.?|inr)\\s?([\\d,]+\\.?\\d*)")
    private val debitRegex = Regex("(?i)(debited|spent|paid|sent|withdrawn)")

    // Load messages based on the selected date
    fun loadMessages(context: Context) {
        viewModelScope.launch {
            val list = fetchAndParseSms(context, _selectedDateMs.value)
            _transactions.value = list
            _totalSpent.value = list.filter { it.isDebit }.sumOf { it.amount }
        }
    }

    fun updateStartDate(context: Context, dateMs: Long) {
        _selectedDateMs.value = dateMs
        loadMessages(context) // Reload when date changes
    }

    // Save a merchant name for a specific sender ID
    fun saveMerchantMapping(context: Context, sender: String, merchantName: String) {
        val prefs = context.getSharedPreferences("merchant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(sender, merchantName).apply()
        loadMessages(context) // Reload to apply the new names
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
                        val savedMerchant = prefs.getString(sender, null)

                        transactions.add(
                            SmsTransaction(
                                id = it.getString(idIdx),
                                sender = sender,
                                body = body,
                                dateMs = it.getLong(dateIdx),
                                amount = amount,
                                isDebit = true,
                                merchantName = savedMerchant
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

// --- ACTIVITY ---

class ExpenseTrackerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ExpenseApp()
                }
            }
        }
    }
}

// --- COMPOSE UI ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseApp(viewModel: ExpenseViewModel = viewModel()) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val selectedDateMs by viewModel.selectedDateMs.collectAsState()
    
    var hasPermission by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadMessages(context)
        } else {
            Toast.makeText(context, "SMS Permission is required", Toast.LENGTH_LONG).show()
        }
    }

    // Initial load
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadMessages(context)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (hasPermission) {
                DashboardHeader(
                    totalSpent = totalSpent, 
                    selectedDateMs = selectedDateMs,
                    onDateSelected = { newDateMs -> viewModel.updateStartDate(context, newDateMs) }
                )
                TransactionList(
                    transactions = transactions,
                    onAssignMerchant = { tx, newName ->
                        viewModel.saveMerchantMapping(context, tx.sender, newName)
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) }) {
                        Text("Grant SMS Permission")
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(totalSpent: Double, selectedDateMs: Long, onDateSelected: (Long) -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMs }
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val newCal = Calendar.getInstance()
            newCal.set(year, month, dayOfMonth, 0, 0, 0)
            onDateSelected(newCal.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Total Spent", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("₹${String.format("%.2f", totalSpent)}", fontSize = 36.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Since: ${sdf.format(Date(selectedDateMs))}")
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { datePickerDialog.show() }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                }
            }
        }
    }
}

@Composable
fun TransactionList(transactions: List<SmsTransaction>, onAssignMerchant: (SmsTransaction, String) -> Unit) {
    var showDialog by remember { mutableStateOf<SmsTransaction?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(transactions) { tx ->
            TransactionItem(tx) {
                showDialog = tx // Show dialog to edit merchant when clicked
            }
        }
    }

    // Merchant assignment dialog
    showDialog?.let { tx ->
        var merchantInput by remember { mutableStateOf(tx.merchantName ?: "") }
        AlertDialog(
            onDismissRequest = { showDialog = null },
            title = { Text("Assign Merchant") },
            text = {
                Column {
                    Text("Sender: ${tx.sender}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = merchantInput,
                        onValueChange = { merchantInput = it },
                        label = { Text("Merchant Name (e.g., Amazon, Swiggy)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAssignMerchant(tx, merchantInput)
                    showDialog = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TransactionItem(tx: SmsTransaction, onClick: () -> Unit) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.merchantName ?: tx.sender, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp
                    )
                    if (tx.merchantName == null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Add Merchant", modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    text = tx.body,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = sdf.format(Date(tx.dateMs)), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(
                text = "₹${tx.amount}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}