package com.expensetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.expensetracker.app.data.Category

/** Pick a category, type an amount, Save. Also used for editing, where it offers Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseDialog(
    title: String,
    categories: List<Category>,
    initialCategoryId: Long?,
    initialAmount: String,
    note: String?,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, amountPaise: Long) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var categoryId by remember { mutableStateOf(initialCategoryId) }
    var amountText by remember {
        mutableStateOf(TextFieldValue(initialAmount, TextRange(initialAmount.length)))
    }
    var showErrors by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val amount = parseAmount(amountText.text)

    fun save() {
        val id = categoryId
        if (id != null && amount != null) onSave(id, amount) else showErrors = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                        )
                    }
                }
                if (showErrors && categoryId == null) {
                    Text(
                        "Pick a category",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        showErrors = false
                    },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = showErrors && amount == null,
                    supportingText = if (showErrors && amount == null) {
                        { Text("Enter an amount like 45 or 12.50") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
