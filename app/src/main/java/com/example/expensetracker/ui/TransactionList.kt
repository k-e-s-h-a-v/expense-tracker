package com.keshav.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keshav.expensetracker.model.SmsTransaction
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionList(
        transactions: List<SmsTransaction>,
        onAssignMerchant: (SmsTransaction, String) -> Unit,
        modifier: Modifier = Modifier
) {
        if (transactions.isEmpty()) {
                Box(
                        modifier = modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                ) {
                        Text(
                                "No transactions found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                        )
                }
                return
        }

        LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                items(transactions, key = { it.id }) { tx ->
                        TransactionItem(transaction = tx, onAssignMerchant = onAssignMerchant)
                }
        }
}

@Composable
private fun TransactionItem(
        transaction: SmsTransaction,
        onAssignMerchant: (SmsTransaction, String) -> Unit
) {
        val sdf = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
        var isEditingMerchant by remember { mutableStateOf(false) }
        var showFullMessage by remember { mutableStateOf(false) }
        var merchantText by
                remember(transaction.id, transaction.merchant) {
                        mutableStateOf(transaction.merchant.orEmpty())
                }

        if (showFullMessage) {
                AlertDialog(
                        onDismissRequest = { showFullMessage = false },
                        title = {
                                Text(
                                        text = transaction.merchant?.takeIf { it.isNotBlank() }
                                                        ?: "Transaction",
                                        style = MaterialTheme.typography.titleMedium
                                )
                        },
                        text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                                text = formatSenderMessage(transaction),
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                        HorizontalDivider()
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                                Text(
                                                        text = sdf.format(Date(transaction.dateMs)),
                                                        style =
                                                                MaterialTheme.typography
                                                                        .labelMedium,
                                                        color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                        text = formatAmount(transaction.amount),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.error
                                                )
                                        }
                                }
                        },
                        confirmButton = {
                                TextButton(onClick = { showFullMessage = false }) { Text("Close") }
                        }
                )
        }

        OutlinedCard(
                colors =
                        CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                        )
        ) {
                Column(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                        if (isEditingMerchant) {
                                CompactTextField(
                                        value = merchantText,
                                        onValueChange = { merchantText = it },
                                        placeholder = "Merchant name",
                                        trailingIcon = {
                                                IconButton(
                                                        onClick = {
                                                                val trimmed = merchantText.trim()
                                                                if (trimmed !=
                                                                                transaction.merchant
                                                                                        .orEmpty()
                                                                ) {
                                                                        onAssignMerchant(
                                                                                transaction,
                                                                                trimmed
                                                                        )
                                                                }
                                                                merchantText = trimmed
                                                                isEditingMerchant = false
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Default.Check,
                                                                contentDescription =
                                                                        "Save merchant",
                                                                modifier = Modifier.size(18.dp),
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                }
                                        },
                                        onDone = {
                                                val trimmed = merchantText.trim()
                                                if (trimmed != transaction.merchant.orEmpty()) {
                                                        onAssignMerchant(transaction, trimmed)
                                                }
                                                merchantText = trimmed
                                                isEditingMerchant = false
                                        }
                                )
                        } else {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text =
                                                        transaction.merchant?.takeIf {
                                                                it.isNotBlank()
                                                        }
                                                                ?: "Unknown merchant",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier =
                                                        Modifier.weight(1f).clickable {
                                                                showFullMessage = true
                                                        },
                                                color =
                                                        if (transaction.merchant.isNullOrBlank())
                                                                MaterialTheme.colorScheme.outline
                                                        else MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                                onClick = {
                                                        merchantText =
                                                                transaction.merchant.orEmpty()
                                                        isEditingMerchant = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = "Edit merchant",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                        }
                                }
                        }

                        Column(
                                modifier =
                                        Modifier.fillMaxWidth().padding(top = 6.dp).clickable {
                                                showFullMessage = true
                                        }
                        ) {
                                Text(
                                        text = formatSenderMessage(transaction),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Text(
                                                text = sdf.format(Date(transaction.dateMs)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                                text = formatAmount(transaction.amount),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = MaterialTheme.colorScheme.error
                                        )
                                }
                        }
                }
        }
}

private fun formatSenderMessage(transaction: SmsTransaction) = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(transaction.sender) }
        append(": ")
        append(transaction.body)
}

private fun formatAmount(amount: Double) = "₹${String.format("%.2f", amount)}"
