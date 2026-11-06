package com.expensetracker.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.app.data.Category
import com.expensetracker.app.ui.theme.categoryColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Lists one level of categories: the top-level cards when [groupId] is null,
 * otherwise the parts of that group. Rows can be dragged to change the Home order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    vm: ExpenseViewModel,
    groupId: Long?,
    onBack: () -> Unit,
    onOpenGroup: (Long) -> Unit,
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val side by vm.side.collectAsStateWithLifecycle()
    val group = groupId?.let { id -> categories.firstOrNull { it.id == id } }
    // Spent and Received categories are kept apart; a group's parts are on the group's side.
    val received = group?.isReceived ?: (side == Side.RECEIVED)
    val groups = categories.filter { it.parentId == null && it.isGroup && it.isReceived == received }
    val levelItems = categories.filter { it.parentId == groupId && it.isReceived == received }

    // Local copy so rows move smoothly while dragging; saved when the drag ends.
    var rows by remember { mutableStateOf(levelItems) }
    LaunchedEffect(levelItems) { rows = levelItems }

    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var moving by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    // The group was deleted (or moved away) while its screen was open.
    if (groupId != null && group == null && categories.isNotEmpty()) {
        LaunchedEffect(Unit) { onBack() }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        rows = rows.toMutableList().apply {
            val fromIndex = indexOfFirst { it.id == from.key }
            val toIndex = indexOfFirst { it.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (group != null) "New item" else "New category") },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (vm.incomeEnabled && groupId == null) {
                item(key = "side") {
                    SingleChoiceSegmentedButtonRow(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Side.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = side == option,
                                onClick = { vm.setSide(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, Side.entries.size),
                            ) {
                                Text(if (option == Side.SPENT) "Spent" else "Received")
                            }
                        }
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Hold ☰ and drag to change the order on the Home screen.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(rows, key = { it.id }) { category ->
                ReorderableItem(reorderState, key = category.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                    val details = description(category, categories)
                    Surface(shadowElevation = elevation) {
                        ListItem(
                            headlineContent = { Text(category.name) },
                            supportingContent = if (details != null) {
                                { Text(details) }
                            } else null,
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(onDragStopped = { vm.reorder(rows) }),
                                    ) {
                                        Icon(Icons.Filled.DragHandle, contentDescription = "Drag to reorder ${category.name}")
                                    }
                                    ColorDot(categoryColor(category.colorIndex))
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryMenu(
                                        category,
                                        onRename = { renaming = category },
                                        onToggleOptional = { vm.setOptional(category, !category.isOptional) },
                                        onMove = { moving = category },
                                        onDelete = { deleting = category },
                                    )
                                    if (category.isGroup) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                    }
                                }
                            },
                            modifier = if (category.isGroup) {
                                Modifier.clickable { onOpenGroup(category.id) }
                            } else Modifier,
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddCategoryDialog(
            groups = groups,
            initialParent = group,
            onDismiss = { adding = false },
            onAdd = { name, parent, isGroup, isOptional ->
                vm.addCategory(name, parent, isGroup, isOptional)
                adding = false
            },
        )
    }

    renaming?.let { category ->
        RenameDialog(
            initialName = category.name,
            onDismiss = { renaming = null },
            onRename = { name ->
                vm.renameCategory(category, name)
                renaming = null
            },
        )
    }

    moving?.let { category ->
        MoveDialog(
            category = category,
            groups = groups,
            onDismiss = { moving = null },
            onMove = { target ->
                vm.moveCategory(category, target)
                moving = null
            },
        )
    }

    deleting?.let { category ->
        var expenseCount by remember(category.id) { mutableStateOf<Int?>(null) }
        LaunchedEffect(category.id) { expenseCount = vm.expenseCount(category) }
        val partCount = categories.count { it.parentId == category.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${category.name}?") },
            text = { Text(deleteMessage(partCount, expenseCount)) },
            confirmButton = {
                TextButton(
                    enabled = expenseCount != null,
                    onClick = {
                        vm.deleteCategory(category)
                        deleting = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

private fun description(category: Category, all: List<Category>): String? = when {
    category.isGroup -> {
        val parts = all.count { it.parentId == category.id }
        if (parts == 1) "Group · 1 item" else "Group · $parts items"
    }
    category.isOptional -> "Shown only when used"
    else -> null
}

private fun deleteMessage(partCount: Int, expenseCount: Int?): String {
    if (expenseCount == null) return "Counting saved expenses…"
    val parts = when (partCount) {
        0 -> null
        1 -> "its 1 item"
        else -> "its $partCount items"
    }
    val expenses = when (expenseCount) {
        0 -> null
        1 -> "1 saved expense from all months"
        else -> "$expenseCount saved expenses from all months"
    }
    val lost = listOfNotNull(parts, expenses)
    return if (lost.isEmpty()) "It has no saved expenses." else "This also deletes ${lost.joinToString(" and ")}."
}

@Composable
private fun CategoryMenu(
    category: Category,
    onRename: () -> Unit,
    onToggleOptional: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${category.name}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
            if (!category.isGroup) {
                DropdownMenuItem(
                    text = { Text("Show only when used") },
                    trailingIcon = { Checkbox(checked = category.isOptional, onCheckedChange = null) },
                    onClick = { open = false; onToggleOptional() },
                )
                DropdownMenuItem(text = { Text("Move to…") }, onClick = { open = false; onMove() })
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCategoryDialog(
    groups: List<Category>,
    initialParent: Category?,
    onDismiss: () -> Unit,
    onAdd: (name: String, parent: Category?, isGroup: Boolean, isOptional: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf(initialParent) }
    var isGroup by remember { mutableStateOf(false) }
    var isOptional by remember { mutableStateOf(false) }
    val makingGroup = parent == null && isGroup

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NameField(name) { name = it }
                if (groups.isNotEmpty()) {
                    Text("Put it inside", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = parent == null,
                            onClick = { parent = null },
                            label = { Text("Nothing (own card)") },
                        )
                        groups.forEach { group ->
                            FilterChip(
                                selected = parent?.id == group.id,
                                onClick = { parent = group },
                                label = { Text(group.name) },
                            )
                        }
                    }
                }
                if (parent == null) {
                    CheckRow("Make it a group with items inside, like Food", isGroup) { isGroup = it }
                }
                if (!makingGroup) {
                    CheckRow("Show only when used (like Dress)", isOptional) { isOptional = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim(), parent, isGroup, isOptional) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}

@Composable
private fun MoveDialog(
    category: Category,
    groups: List<Category>,
    onDismiss: () -> Unit,
    onMove: (Category?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${category.name} to") },
        text = {
            Column {
                if (category.parentId != null) {
                    ListItem(
                        headlineContent = { Text("Its own card") },
                        modifier = Modifier.clickable { onMove(null) },
                    )
                }
                groups.filter { it.id != category.parentId }.forEach { group ->
                    ListItem(
                        headlineContent = { Text(group.name) },
                        leadingContent = { ColorDot(categoryColor(group.colorIndex)) },
                        modifier = Modifier.clickable { onMove(group) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameDialog(initialName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = { NameField(name) { name = it } },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onRename(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
}
