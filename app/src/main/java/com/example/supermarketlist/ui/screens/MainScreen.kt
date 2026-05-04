package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.example.supermarketlist.data.local.entity.ShoppingItem
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay

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
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }

    var selectedItemForAction by remember { mutableStateOf<ShoppingItem?>(null) }
    var showItemActionDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf(false) }
    var showUncategorizedConfirmDialog by remember { mutableStateOf(false) }
    var showStartShoppingCategoryDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onNavigateToCategories) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Manage Categories")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "History")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Voice Button
                FloatingActionButton(
                    onClick = onStartVoiceInput,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mic), contentDescription = "Add by Voice")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Typing Button
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                val grouped = items.groupBy { item ->
                    if (item.categoryId == null) "Uncategorized"
                    else categories.find { it.id == item.categoryId }?.name ?: "Uncategorized"
                }

                val sortedCategoryNames = grouped.keys.sortedWith { a, b ->
                    if (a == "Uncategorized") 1 else if (b == "Uncategorized") -1 else a.compareTo(b)
                }

                sortedCategoryNames.forEach { categoryName ->
                    val categoryItems = grouped[categoryName] ?: emptyList()
                    val sortedItems = categoryItems.sortedBy { it.isChecked }

                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = categoryName,
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    items(sortedItems, key = { it.id }) { item ->
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
                onAddCategory = { name, onDone ->
                    viewModel.addCategory(name, onDone)
                }
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
                onAddCategory = { name, onDone ->
                    viewModel.addCategory(name, onDone)
                }
            )
        }

        if (showStartShoppingCategoryDialog) {
            var selectedCatId by remember { mutableStateOf<Long?>(null) }
            AlertDialog(
                onDismissRequest = { showStartShoppingCategoryDialog = false },
                title = { Text("Select Category to Shop") },
                text = {
                    CategoryDropdownWithAdd(
                        categories = categories,
                        selectedCategoryId = selectedCatId,
                        onCategorySelected = { selectedCatId = it },
                        onAddNewCategory = {}, // Not needed here
                        lastUsedCategoryId = null
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onStartShopping(selectedCatId)
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
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Type the item name manually", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mic), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
            modifier = Modifier.fillMaxWidth(0.5f)
        ) {
            Text("Manage Categories")
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
                Text("Category (Optional):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))

                CategoryDropdownWithAdd(
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = { selectedCategoryId = it },
                    onAddNewCategory = { showNewCategoryDialog = true },
                    lastUsedCategoryId = initialCategoryId
                )
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
fun CategoryDropdownWithAdd(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    onAddNewCategory: () -> Unit,
    lastUsedCategoryId: Long? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    val sortedCategories = remember(categories, lastUsedCategoryId) {
        categories.sortedWith { a, b ->
            if (a.id == lastUsedCategoryId) -1
            else if (b.id == lastUsedCategoryId) 1
            else a.name.compareTo(b.name)
        }
    }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedCategory?.name ?: "No Category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onCategorySelected(null)
                    expanded = false
                }
            )
            sortedCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("+ Create New Category", color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    onAddNewCategory()
                    expanded = false
                }
            )
        }
    }
}
