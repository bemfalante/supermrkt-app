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
import com.example.supermarketlist.R
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.data.local.entity.ItemPriceHistory
import com.example.supermarketlist.data.local.entity.ShoppingItem
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    var newItemName by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    var selectedItemForAction by remember { mutableStateOf<ShoppingItem?>(null) }
    var showItemActionDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf(false) }
    var showUncategorizedConfirmDialog by remember { mutableStateOf(false) }
    var showStartShoppingCategoryDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }

    var showSettingsMenu by remember { mutableStateOf(false) }

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
                        Button(
                            onClick = { showStartShoppingCategoryDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Shopping!", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                    DropdownMenu(expanded = showSettingsMenu, onDismissRequest = { showSettingsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Manage Categories") },
                            onClick = {
                                showSettingsMenu = false
                                onNavigateToCategories()
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Shopping History") },
                            onClick = {
                                showSettingsMenu = false
                                onNavigateToHistory()
                            },
                            leadingIcon = { Icon(Icons.Default.ShoppingCart, null) }
                        )
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
                        selectedCategoryId = viewModel.lastUsedCategoryId
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
            val categorized = items.groupBy { it.categoryId }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                categories.forEach { category ->
                    val categoryItems = categorized[category.id] ?: emptyList()
                    item(key = "header_${category.id}") {
                        CategoryHeader(category.name)
                    }
                    items(categoryItems.sortedBy { it.isChecked }, key = { "item_${it.id}" }) { item ->
                        ShoppingItemRow(
                            item = item,
                            onToggle = { viewModel.toggleItem(item) },
                            onLongPress = {
                                selectedItemForAction = item
                                showItemActionDialog = true
                            }
                        )
                    }
                }

                val uncategorized = categorized[null] ?: emptyList()
                if (uncategorized.isNotEmpty()) {
                    item(key = "header_uncategorized") {
                        CategoryHeader("Uncategorized")
                    }
                    items(uncategorized.sortedBy { it.isChecked }, key = { "item_uncat_${it.id}" }) { item ->
                        ShoppingItemRow(
                            item = item,
                            onToggle = { viewModel.toggleItem(item) },
                            onLongPress = {
                                selectedItemForAction = item
                                showItemActionDialog = true
                            }
                        )
                    }
                }
            }
        }

        if (showAddItemDialog) {
            AddEditItemDialog(
                title = "Add Item",
                initialName = newItemName,
                initialCategoryId = selectedCategoryId,
                categories = categories,
                onDismiss = { showAddItemDialog = false },
                onConfirm = { name, catId ->
                    newItemName = name
                    selectedCategoryId = catId
                    if (catId == null) {
                        showUncategorizedConfirmDialog = true
                    } else {
                        viewModel.addItem(name, catId)
                        newItemName = ""
                        selectedCategoryId = null
                        showAddItemDialog = false
                    }
                },
                onAddCategory = { name, onDone -> viewModel.addCategory(name, onDone) }
            )
        }

        if (showUncategorizedConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showUncategorizedConfirmDialog = false },
                title = { Text("No Category Selected") },
                text = { Text("Are you sure you want to add this item to the Uncategorized list?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.addItem(newItemName, null)
                        newItemName = ""
                        selectedCategoryId = null
                        showUncategorizedConfirmDialog = false
                        showAddItemDialog = false
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUncategorizedConfirmDialog = false }) {
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
             AddEditItemDialog(
                title = "Edit Item",
                initialName = selectedItemForAction!!.name,
                initialCategoryId = selectedItemForAction!!.categoryId,
                categories = categories,
                onDismiss = { showEditItemDialog = false },
                onConfirm = { name, catId ->
                    viewModel.updateItem(selectedItemForAction!!.copy(name = name, categoryId = catId))
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

        if (showStartShoppingCategoryDialog) {
            var selectedCatId by remember { mutableStateOf<Long?>(null) }
            AlertDialog(
                onDismissRequest = { showStartShoppingCategoryDialog = false },
                title = { Text("Select Category to Shop") },
                text = {
                    CategoryDropdownSimple(
                        categories = categories,
                        selectedCategoryId = selectedCatId,
                        onCategorySelected = { selectedCatId = it }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.startShopping(selectedCatId)
                        showStartShoppingCategoryDialog = false
                    }) {
                        Text("Start")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStartShoppingCategoryDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun CategoryHeader(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.titleMedium
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddEditItemDialog(
    title: String,
    initialName: String,
    initialCategoryId: Long?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, Long?) -> Unit,
    onAddCategory: (String, (Long) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedCategoryId by remember { mutableStateOf(initialCategoryId) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                val nameFocusRequester = remember { FocusRequester() }
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
                Text("Category (scroll and select):", style = MaterialTheme.typography.bodyMedium)

                Box(modifier = Modifier
                    .heightIn(max = 200.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                ) {
                    LazyColumn {
                        items(categories) { category ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { selectedCategoryId = category.id })
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = selectedCategoryId == category.id, onClick = { selectedCategoryId = category.id })
                                Text(category.name)
                            }
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { selectedCategoryId = null })
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = selectedCategoryId == null, onClick = { selectedCategoryId = null })
                                Text("None")
                            }
                        }
                    }
                }

                TextButton(onClick = { showNewCategoryDialog = true }) {
                    Text("+ Create New Category")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(name, selectedCategoryId)
                }
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
                            selectedCategoryId = newId
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
fun CategoryDropdownSimple(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedCategory?.name ?: "Uncategorized")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Uncategorized") },
                onClick = {
                    onCategorySelected(null)
                    expanded = false
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
