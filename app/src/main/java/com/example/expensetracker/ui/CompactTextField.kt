package com.example.expensetracker.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun CompactTextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier: Modifier = Modifier,
        placeholder: String = "",
        leadingIcon: (@Composable () -> Unit)? = null,
        trailingIcon: (@Composable () -> Unit)? = null,
        singleLine: Boolean = true,
        onDone: (() -> Unit)? = null,
) {
        val shape = RoundedCornerShape(8.dp)
        val colors = MaterialTheme.colorScheme

        BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = modifier.fillMaxWidth().height(36.dp).clip(shape).border(1.dp, colors.outline, shape),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.onSurface),
                singleLine = singleLine,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                decorationBox = { innerTextField ->
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                if (leadingIcon != null) {
                                        leadingIcon()
                                        Spacer(modifier = Modifier.width(6.dp))
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                                                Text(
                                                        text = placeholder,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                )
                                        }
                                        innerTextField()
                                }
                                trailingIcon?.invoke()
                        }
                }
        )
}
