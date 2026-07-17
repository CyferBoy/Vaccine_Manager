package com.clinic.neochild.features.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val date = datePickerState.selectedDateMillis?.let {
                        SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(it))
                    } ?: ""
                    onConfirm(date, reason)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(state = datePickerState)
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
