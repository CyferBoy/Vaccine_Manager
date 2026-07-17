package com.clinic.neochild.core.common

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.clinic.neochild.core.constants.Constants
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reusable background wrapper that applies a simple clean background.
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        content()
    }
}

/**
 * Reusable TopAppBar that supports a search mode.
 * Automatically handles the search toggle and UI changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopAppBar(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    placeholder: String = "Search...",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }

    if (isSearchActive) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.onPrimary,
                        focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = { 
                        onSearchActiveChange(false)
                        onSearchQueryChange("")
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Search", tint = MaterialTheme.colorScheme.onPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
        )
    } else {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                }
            },
            actions = {
                actions()
                IconButton(onClick = { onSearchActiveChange(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

/**
 * Standardized Delete Confirmation Dialog to maintain UI consistency across the app.
 */
@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Delete Confirmation",
    message: String = "Are you sure you want to delete this item? This action cannot be undone."
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dropdown menu typically used for item actions like Edit and Delete.
 */
@Composable
fun ActionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrint: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onMerge: (() -> Unit)? = null,
    onMarkAsDone: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (onMarkAsDone != null) {
            DropdownMenuItem(
                text = { Text("Mark as Done") },
                onClick = {
                    onDismiss()
                    onMarkAsDone()
                },
                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50)) }
            )
            HorizontalDivider()
        }
        if (onPrint != null) {
            DropdownMenuItem(
                text = { Text("Print Receipt") },
                onClick = {
                    onDismiss()
                    onPrint()
                },
                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
            )
        }
        if (onDownload != null) {
            DropdownMenuItem(
                text = { Text("Download Receipt") },
                onClick = {
                    onDismiss()
                    onDownload()
                },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
            )
        }
        DropdownMenuItem(
            text = { Text("Edit") },
            onClick = {
                onDismiss()
                onEdit()
            },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
        )
        if (onMerge != null) {
            DropdownMenuItem(
                text = { Text("Merge") },
                onClick = {
                    onDismiss()
                    onMerge()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null) }
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                onDismiss()
                onDelete()
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
        )
    }
}

/**
 * A generic sort button that opens a menu with provided options.
 * Shows a Toast message when a sort option is selected.
 */
@Composable
fun <T> SortButton(
    options: List<Pair<T, String>>,
    onSortSelected: (T, String) -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = iconColor
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSortSelected(option, label)
                        expanded = false
                        Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

/**
 * Helper function to delete a document from Firestore and show a Toast.
 */
fun deleteFirestoreDocument(
    context: android.content.Context,
    collectionPath: String,
    documentId: String,
    onSuccess: () -> Unit = {}
) {
    FirebaseFirestore.getInstance().collection(collectionPath).document(documentId).delete()
        .addOnSuccessListener {
            Toast.makeText(context, "Deleted successfully", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
}

/**
 * Standardized Button with width constraints for better UI on large screens.
 */
@Composable
fun StandardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    isLoading: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = shape
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            content()
        }
    }
}

@Composable
fun StandardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                focusedTrailingIconColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * Dropdown-style date picker that allows selecting Day, Month, and Year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDropdownPicker(
    label: String,
    currentDate: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance()
    
    // Parse current date or use today
    if (currentDate.isNotEmpty()) {
        try {
            val date = SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).parse(currentDate)
            if (date != null) calendar.time = date
        } catch (_: Exception) {}
    }

    val day = calendar[Calendar.DAY_OF_MONTH]
    val month = calendar[Calendar.MONTH]
    val year = calendar[Calendar.YEAR]

    var expandedDay by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Day Dropdown
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    onClick = { expandedDay = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (expandedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentDate.isEmpty()) "Day" else day.toString(), 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                DropdownMenu(
                    expanded = expandedDay, 
                    onDismissRequest = { expandedDay = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    (1..daysInMonth).forEach { d ->
                        DropdownMenuItem(text = { Text(d.toString()) }, onClick = {
                            calendar[Calendar.DAY_OF_MONTH] = d
                            onDateSelected(SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(calendar.time))
                            expandedDay = false
                        })
                    }
                }
            }

            // Month Dropdown
            Box(modifier = Modifier.weight(1.5f)) {
                val months = SimpleDateFormat("MMM", Locale.ENGLISH).let { fmt ->
                    (0..11).map { m -> 
                        val cal = Calendar.getInstance().apply { set(Calendar.MONTH, m) }
                        fmt.format(cal.time)
                    }
                }
                OutlinedCard(
                    onClick = { expandedMonth = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (expandedMonth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentDate.isEmpty()) "Month" else months[month], 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                    months.forEachIndexed { index, m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = {
                            calendar[Calendar.MONTH] = index
                            onDateSelected(SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(calendar.time))
                            expandedMonth = false
                        })
                    }
                }
            }

            // Year Dropdown
            Box(modifier = Modifier.weight(1.5f)) {
                OutlinedCard(
                    onClick = { expandedYear = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (expandedYear) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentDate.isEmpty()) "Year" else year.toString(), 
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.DateRange, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                DropdownMenu(
                    expanded = expandedYear, 
                    onDismissRequest = { expandedYear = false },
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    val currentYear = Calendar.getInstance()[Calendar.YEAR]
                    // Create list of years from 10 years in future to 100 years ago.
                    // We reverse the order so the most relevant (current) years are at the TOP.
                    ((currentYear + 10) downTo (currentYear - 100)).forEach { y ->
                        DropdownMenuItem(text = { Text(y.toString()) }, onClick = {
                            calendar[Calendar.YEAR] = y
                            onDateSelected(SimpleDateFormat(Constants.DATE_FORMAT, Locale.ENGLISH).format(calendar.time))
                            expandedYear = false
                        })
                    }
                }
            }
        }
    }
}

/**
 * Standardized AutoComplete/Dropdown field for the app.
 * Combines a TextField with a suggestions' menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardAutoCompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier.fillMaxWidth()
    ) {
        StandardTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, true),
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = dropdownContent
        )
    }
}
