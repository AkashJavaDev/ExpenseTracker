package com.expensetracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.app.data.Category
import com.expensetracker.app.data.Expense
import com.expensetracker.app.ui.theme.categoryColor
import kotlinx.coroutines.launch
import java.time.YearMonth

private sealed interface DialogState {
    /** [choices] limits the category chips, e.g. to the parts of one group. */
    data class Add(val category: Category?, val choices: List<Category>? = null) : DialogState
    data class Edit(val expense: Expense) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: ExpenseViewModel, onOpenStats: () -> Unit, onOpenCategories: () -> Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val leaves by vm.leafCategories.collectAsStateWithLifecycle()
    val side by vm.side.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker") },
                actions = {
                    PrintButton(vm)
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Outlined.PieChart, contentDescription = "Month summary")
                    }
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.Outlined.Category, contentDescription = "Categories")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = DialogState.Add(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
        bottomBar = { if (vm.incomeEnabled) SideTabs(side, vm::setSide) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MonthHeader(
                    month,
                    summary.total,
                    vm::previousMonth,
                    vm::nextMonth,
                    breakdownText(summary),
                    totalLabel = totalLabel(side),
                    balance = balance,
                )
            }
            if (summary.sections.isEmpty()) {
                item {
                    Text(
                        "No categories yet. Add one from the Categories screen.",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(summary.sections, key = { it.category.id }) { section ->
                SectionCard(
                    section,
                    onAdd = { dialog = DialogState.Add(it) },
                    // Offer every part of the group, including optional ones hidden this month.
                    onAddToGroup = { group -> dialog = DialogState.Add(null, leaves.filter { it.parentId == group.id }) },
                    onEdit = { dialog = DialogState.Edit(it) },
                )
            }
        }
    }

    val pastMonthNote = if (month != YearMonth.now()) "This will be saved in ${formatMonth(month)}." else null
    when (val d = dialog) {
        is DialogState.Add -> ExpenseDialog(
            title = if (side == Side.RECEIVED) "Add money received" else "Add expense",
            categories = d.choices ?: leaves,
            initialCategoryId = d.category?.id,
            initialAmount = "",
            note = pastMonthNote,
            onDismiss = { dialog = null },
            onSave = { categoryId, paise ->
                vm.addExpense(categoryId, paise)
                dialog = null
            },
        )
        is DialogState.Edit -> ExpenseDialog(
            title = if (side == Side.RECEIVED) "Edit money received" else "Edit expense",
            categories = leaves,
            initialCategoryId = d.expense.categoryId,
            initialAmount = formatAmount(d.expense.amountPaise),
            note = null,
            onDismiss = { dialog = null },
            onSave = { categoryId, paise ->
                vm.updateExpense(d.expense.copy(categoryId = categoryId, amountPaise = paise))
                dialog = null
            },
            onDelete = {
                vm.deleteExpense(d.expense)
                dialog = null
                scope.launch {
                    val result = snackbar.showSnackbar(
                        "Deleted ${formatRupees(d.expense.amountPaise)}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.restoreExpense(d.expense)
                }
            },
        )
        null -> Unit
    }
}

/** One card per top-level category, laid out like the note: name, amounts, total. */
@Composable
private fun SectionCard(
    section: Section,
    onAdd: (Category) -> Unit,
    onAddToGroup: (Category) -> Unit,
    onEdit: (Expense) -> Unit,
) {
    val category = section.category
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(categoryColor(category.colorIndex))
                Spacer(Modifier.width(8.dp))
                Text(category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    formatRupees(section.total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                AddButton(category, if (category.isGroup) onAddToGroup else onAdd)
            }
            if (category.isGroup) {
                if (section.lines.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Nothing yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                section.lines.forEach { line ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            line.category.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatRupees(line.total), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        AddButton(line.category, onAdd)
                    }
                    Spacer(Modifier.height(6.dp))
                    AmountChips(line.expenses, onEdit)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                AmountChips(section.lines.single().expenses, onEdit)
            }
        }
    }
}

/** Bottom tabs of the share version: money spent and money received. */
@Composable
private fun SideTabs(side: Side, onSelect: (Side) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = side == Side.SPENT,
            onClick = { onSelect(Side.SPENT) },
            icon = { Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null) },
            label = { Text("Spent") },
        )
        NavigationBarItem(
            selected = side == Side.RECEIVED,
            onClick = { onSelect(Side.RECEIVED) },
            icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null) },
            label = { Text("Received") },
        )
    }
}

@Composable
private fun AddButton(category: Category, onAdd: (Category) -> Unit) {
    FilledTonalIconButton(onClick = { onAdd(category) }, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Filled.Add, contentDescription = "Add to ${category.name}", modifier = Modifier.size(18.dp))
    }
}
