package com.example.expensetracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHeader(totalSpent: Double, selectedDateMs: Long, onDateSelected: (Long) -> Unit) {
  val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
  var showDatePicker by remember { mutableStateOf(false) }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMs)
    DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
              TextButton(
                      onClick = {
                        datePickerState.selectedDateMillis?.let { onDateSelected(it) }
                        showDatePicker = false
                      }
              ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
    ) { DatePicker(state = datePickerState) }
  }

  Card(
          modifier =
                  Modifier.fillMaxWidth()
                          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
          colors =
                  CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
    ) {
      Column {
        Text(
                text =
                        buildAnnotatedString {
                          append("Total spent since ")

                          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(sdf.format(Date(selectedDateMs)))
                          }
                        },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
                text = "₹${String.format("%.2f", totalSpent)}",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
        )
      }
      Spacer(modifier = Modifier.weight(1f))

      IconButton(onClick = { showDatePicker = true }) {
        Icon(
                Icons.Default.DateRange,
                contentDescription = "Select Date",
                tint = MaterialTheme.colorScheme.primary
        )
      }
    }
  }
}
