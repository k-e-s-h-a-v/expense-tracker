package com.example.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.model.SmsTransaction
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionList(transactions: List<SmsTransaction>, onAssignMerchant: (SmsTransaction, String) -> Unit) {
    var showDialog by remember { mutableStateOf<SmsTransaction?>(null) }

    if (transactions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No transactions found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(transactions) { tx ->
                TransactionItem(tx) {
                    showDialog = tx // Show dialog to edit merchant when clicked
                }
            }
        }
    }

    // Merchant assignment dialog
    showDialog?.let { tx ->
        var merchantInput by remember { mutableStateOf(tx.merchant ?: "") }
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
                        text = tx.merchant ?: tx.sender, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tx.merchant == null) {
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
