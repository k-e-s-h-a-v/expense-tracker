package com.keshav.expensetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHeader(
    totalSpent: Double,
    selectedDateMs: Long,
    selectedEndDateMs: Long,
    hasRollingEndDate: Boolean,
    onStartDateSelected: (Long) -> Unit,
    onEndDateSelected: (Long) -> Unit,
    onRollingEndDateChange: (Boolean) -> Unit
) {
  val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
  var showStartPicker by remember { mutableStateOf(false) }
  var showEndPicker by remember { mutableStateOf(false) }

  if (showStartPicker) {
    val state = rememberDatePickerState(initialSelectedDateMillis = selectedDateMs)
    DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
              TextButton(onClick = {
                state.selectedDateMillis?.let(onStartDateSelected)
                showStartPicker = false
              }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
    ) { DatePicker(state = state) }
  }

  if (showEndPicker) {
    val state = rememberDatePickerState(initialSelectedDateMillis = selectedEndDateMs)
    DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
              TextButton(onClick = {
                state.selectedDateMillis?.let(onEndDateSelected)
                showEndPicker = false
              }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
    ) { DatePicker(state = state) }
  }

  Card(
          modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
                text = "₹${String.format("%.2f", totalSpent)}",
                fontSize = 32.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
        )
        Spacer(Modifier.width(8.dp))
        RollingEndDateToggle(
                checked = hasRollingEndDate,
                onCheckedChange = onRollingEndDateChange,
                modifier = Modifier.weight(1f)
        )
      }
      Spacer(Modifier.height(16.dp))
      DateRangeControl(
              startLabel = dateFormat.format(Date(selectedDateMs)),
              endLabel = if (hasRollingEndDate) "Now" else dateFormat.format(Date(selectedEndDateMs)),
              onStartClick = { showStartPicker = true },
              onEndClick = { showEndPicker = true }
      )
    }
  }
}

@Composable
private fun RollingEndDateToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  Column(
          modifier = modifier,
          horizontalAlignment = Alignment.End
  ) {
    Text(
            text = "Rolling end date (Today)",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
    )
    Canvas(
            modifier =
                    Modifier.size(width = 38.dp, height = 20.dp)
                            .toggleable(
                                    value = checked,
                                    role = Role.Switch,
                                    onValueChange = onCheckedChange
                            )
    ) {
      val trackColor = if (checked) colors.primary else colors.outline
      val thumbColor = if (checked) colors.onPrimary else colors.surface
      val trackHeight = 16.dp.toPx()
      val radius = trackHeight / 2
      val top = (size.height - trackHeight) / 2
      drawRoundRect(
              color = trackColor,
              topLeft = androidx.compose.ui.geometry.Offset(0f, top),
              size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
              cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
      )
      drawCircle(
              color = thumbColor,
              radius = 6.dp.toPx(),
              center =
                      androidx.compose.ui.geometry.Offset(
                              if (checked) size.width - 10.dp.toPx() else 10.dp.toPx(),
                              size.height / 2
                      )
      )
    }
  }
}

@Composable
private fun DateRangeControl(
    startLabel: String,
    endLabel: String,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit
) {
  val color = MaterialTheme.colorScheme.primary
  val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth()) {
      Text(startLabel, modifier = Modifier.weight(1f).clickable(onClick = onStartClick))
      Text(endLabel, modifier = Modifier.clickable(onClick = onEndClick))
    }
    Canvas(
            modifier = Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 4.dp)
    ) {
      val radius = 5.dp.toPx()
      val centerY = size.height / 2
      drawLine(color, start = androidx.compose.ui.geometry.Offset(radius, centerY), end = androidx.compose.ui.geometry.Offset(size.width - radius, centerY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
      drawCircle(backgroundColor, radius = radius, center = androidx.compose.ui.geometry.Offset(radius, centerY))
      drawCircle(backgroundColor, radius = radius, center = androidx.compose.ui.geometry.Offset(size.width - radius, centerY))
      drawCircle(color, radius = radius, center = androidx.compose.ui.geometry.Offset(radius, centerY), style = Stroke(2.dp.toPx()))
      drawCircle(color, radius = radius, center = androidx.compose.ui.geometry.Offset(size.width - radius, centerY), style = Stroke(2.dp.toPx()))
    }
  }
}
