package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import com.example.supermarketlist.R
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.data.local.entity.ShoppingItem
import com.example.supermarketlist.data.local.entity.ShoppingItemWithCategoryIds
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: ShoppingViewModel,
    onNavigateToCategories: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onStartShopping: (Long?) -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    var showAddItemDialog by remember { mutableStateOf(false) }
    var initialCategoryIdsForAdd by remember { mutableStateOf<List<Long>>(emptyList()) }

    var selectedItemForAction by remember { mutableStateOf<ShoppingItem?>(null) }
    var showItemActionDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    val expandedCategories = remember { mutableStateMapOf<Long?, Boolean>() }

    val scope = rememberCoroutineScope()

    LaunchedEffect(activeSession) {
        activeSession?.let {
            onStartShopping(it.categoryId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Market List")
                        Spacer(modifier = Modifier.width(8.dp))
                        if (activeSession != null) {
                            IconButton(
                                onClick = { showCancelConfirmDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Shopping", tint = Color.Red)
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToCategories) {
                        Text("Categories", style = MaterialTheme.typography.labelLarge)
                    }
                    TextButton(onClick = onNavigateToHistory) {
                        Text("History", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = onStartVoiceInput,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mic), contentDescription = "Add by Voice")
                }

                Spacer(modifier = Modifier.height(16.dp))

                FloatingActionButton(
                    onClick = {
                        initialCategoryIdsForAdd = viewModel.lastUsedCategoryId?.let { listOf(it) } ?: emptyList()
                        showAddItemDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            WelcomeScreen(modifier = Modifier.padding(padding), onManageCategories = onNavigateToCategories)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                item {
                    val allExpanded = categories.all { expandedCategories[it.id] == true } &&
                                    (items.none { it.categoryIds.isEmpty() } || expandedCategories[null] == true)

                    Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = {
                            val target = !allExpanded
                            categories.forEach { expandedCategories[it.id] = target }
                            expandedCategories[null] = target
                        }) {
                            Text(if (allExpanded) "Hide All" else "Show All")
                        }
                    }
                }

                categories.forEach { category ->
                    item(key = "header_${category.id}") {
                        CategoryHeader(
                            name = category.name,
                            isExpanded = expandedCategories[category.id] ?: false,
                            onToggle = {
                                expandedCategories[category.id] = !(expandedCategories[category.id] ?: false)
                            },
                            onAddItem = {
                                initialCategoryIdsForAdd = listOf(category.id)
                                showAddItemDialog = true
                            },
                            onStartShopping = { onStartShopping(category.id) }
                        )
                    }
                    if (expandedCategories[category.id] == true) {
                        val categoryItems = items.filter { it.categoryIds.contains(category.id) }
                        items(categoryItems, key = { "cat_${category.id}_item_${it.item.id}" }) { itemWithIds ->
                            ShoppingItemRow(
                                item = itemWithIds.item,
                                onToggle = { viewModel.toggleItem(itemWithIds.item) },
                                onLongPress = {
                                    selectedItemForAction = itemWithIds.item
                                    showItemActionDialog = true
                                }
                            )
                        }
                    }
                }

                val uncategorized = items.filter { it.categoryIds.isEmpty() }
                if (uncategorized.isNotEmpty()) {
                    item(key = "header_uncategorized") {
                        CategoryHeader(
                            name = "Uncategorized",
                            isExpanded = expandedCategories[null] ?: false,
                            onToggle = {
                                expandedCategories[null] = !(expandedCategories[null] ?: false)
                            },
                            onAddItem = {
                                initialCategoryIdsForAdd = emptyList()
                                showAddItemDialog = true
                            },
                            onStartShopping = { onStartShopping(null) }
                        )
                    }
                    if (expandedCategories[null] == true) {
                        items(uncategorized, key = { "uncat_item_${it.item.id}" }) { itemWithIds ->
                            ShoppingItemRow(
                                item = itemWithIds.item,
                                onToggle = { viewModel.toggleItem(itemWithIds.item) },
                                onLongPress = {
                                    selectedItemForAction = itemWithIds.item
                                    showItemActionDialog = true
                                }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (showAddItemDialog) {
            AddEditItemDialog(
                title = "Add Item",
                initialName = "",
                initialCategoryIds = initialCategoryIdsForAdd,
                categories = categories,
                isSequential = true,
                onDismiss = { showAddItemDialog = false },
                onConfirm = { name, catIds ->
                    viewModel.addItem(name, catIds)
                },
                onAddCategory = { name, onDone -> viewModel.addCategory(name, onDone) }
            )
        }

        if (showCancelConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCancelConfirmDialog = false },
                title = { Text("Cancel Shopping?") },
                text = { Text("Are you sure you want to cancel the current shopping session? All progress will be lost.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            viewModel.cancelShopping()
                            showCancelConfirmDialog = false
                        }
                    }) {
                        Text("Yes, Cancel", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelConfirmDialog = false }) {
                        Text("No")
                    }
                }
            )
        }

        if (showItemActionDialog && selectedItemForAction != null) {
            AlertDialog(
                onDismissRequest = { showItemActionDialog = false },
                title = { Text(selectedItemForAction!!.name) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                showItemActionDialog = false
                                showEditItemDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Edit")
                            }
                        }
                        TextButton(
                            onClick = {
                                showItemActionDialog = false
                                showPriceHistoryDialog = selectedItemForAction!!.name
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Price History")
                            }
                        }
                        TextButton(
                            onClick = {
                                viewModel.deleteItem(selectedItemForAction!!)
                                showItemActionDialog = false
                                selectedItemForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete")
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        if (showEditItemDialog && selectedItemForAction != null) {
             var currentCatIds by remember { mutableStateOf<List<Long>>(emptyList()) }
             LaunchedEffect(selectedItemForAction) {
                 currentCatIds = viewModel.getCategoryIdsForItem(selectedItemForAction!!.id)
             }

             AddEditItemDialog(
                title = "Edit Item",
                initialName = selectedItemForAction!!.name,
                initialCategoryIds = currentCatIds,
                categories = categories,
                isSequential = false,
                onDismiss = { showEditItemDialog = false },
                onConfirm = { name, catIds ->
                    viewModel.updateItem(selectedItemForAction!!.copy(name = name), catIds)
                    showEditItemDialog = false
                    selectedItemForAction = null
                },
                onAddCategory = { name, onDone -> viewModel.addCategory(name, onDone) }
            )
        }

        if (showPriceHistoryDialog != null) {
            PriceHistoryDialog(
                itemName = showPriceHistoryDialog!!,
                viewModel = viewModel,
                onDismiss = { showPriceHistoryDialog = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryHeader(
    name: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onAddItem: () -> Unit,
    onStartShopping: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onToggle,
            onLongClick = null
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Row {
                IconButton(onClick = onAddItem, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add to this category", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onStartShopping,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Shopping!", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(modifier: Modifier = Modifier, onManageCategories: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Market List!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Start building your list by adding items in two ways:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Type the item name manually", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mic), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use your voice to add items quickly", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "You can also organize your items into categories to make shopping easier.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onManageCategories,
            modifier = Modifier.width(180.dp)
        ) {
            Text("Manage Categories", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(item: ShoppingItem, onToggle: () -> Unit, onLongPress: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = { onToggle() },
                onLongClick = { onLongPress() }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.isChecked, onCheckedChange = { onToggle() })
        Text(
            text = item.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemDialog(
    title: String,
    initialName: String,
    initialCategoryIds: List<Long>,
    categories: List<Category>,
    isSequential: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Long>) -> Unit,
    onAddCategory: (String, (Long) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategoryIds by remember { mutableStateOf(initialCategoryIds) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val nameFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester)
                )

                LaunchedEffect(Unit) {
                    delay(100)
                    nameFocusRequester.requestFocus()
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Categories:", style = MaterialTheme.typography.bodyMedium)

                CategoryMultiSelectDropdown(
                    allCategories = categories,
                    selectedIds = selectedCategoryIds,
                    onToggle = { id ->
                        selectedCategoryIds = if (selectedCategoryIds.contains(id)) {
                            selectedCategoryIds - id
                        } else {
                            selectedCategoryIds + id
                        }
                    },
                    onAddNew = { showNewCategoryDialog = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name, selectedCategoryIds)
                    if (isSequential) {
                        name = ""
                        // Keep open
                    } else {
                        onDismiss()
                    }
                }
            }) {
                Text(if (isSequential) "Add & Next" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isSequential && name.isNotBlank()) {
                    onConfirm(name, selectedCategoryIds)
                }
                onDismiss()
            }) {
                Text(if (isSequential) "Add & Finish" else "Cancel")
            }
        }
    )

    if (showNewCategoryDialog) {
        val catFocusRequester = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("New Category") },
            text = {
                TextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.focusRequester(catFocusRequester)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onAddCategory(newCategoryName) { newId ->
                            selectedCategoryIds = selectedCategoryIds + newId
                        }
                        newCategoryName = ""
                        showNewCategoryDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
        LaunchedEffect(Unit) {
            delay(100)
            catFocusRequester.requestFocus()
        }
    }
}

@Composable
fun CategoryMultiSelectDropdown(
    allCategories: List<Category>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit,
    onAddNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            val text = if (selectedIds.isEmpty()) "Uncategorized"
                      else allCategories.filter { selectedIds.contains(it.id) }.joinToString { it.name }
            Text(text, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.ArrowDropDown, null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 300.dp)
        ) {
            allCategories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedIds.contains(category.id), onCheckedChange = null)
                            Text(category.name)
                        }
                    },
                    onClick = { onToggle(category.id) }
                )
            }
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selectedIds.isEmpty(), onCheckedChange = null)
                        Text("Uncategorized")
                    }
                },
                onClick = { /* In many-to-many, empty list means uncategorized */ }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ Create New Category", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    expanded = false
                    onAddNew()
                }
            )
        }
    }
}

@Composable
fun PriceHistoryDialog(
    itemName: String,
    viewModel: ShoppingViewModel,
    onDismiss: () -> Unit
) {
    val history by viewModel.getPriceHistoryForItem(itemName).collectAsState(initial = emptyList())
    val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Price History: $itemName") },
        text = {
            if (history.isEmpty()) {
                Text("No price history found.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(history) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(entry.categoryName, style = MaterialTheme.typography.bodySmall)
                                Text(dateFormat.format(Date(entry.timestamp)), style = MaterialTheme.typography.labelSmall)
                            }
                            val ptBr = Locale("pt", "BR")
                            Text(
                                text = "R$ ${String.format(ptBr, "%.2f", entry.price)} (Qty: ${entry.quantity})",
                                color = if(entry.status == "BOUGHT") Color(0xFF4CAF50) else Color.Red,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
