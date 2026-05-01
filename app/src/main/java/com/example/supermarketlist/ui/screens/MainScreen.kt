package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.data.local.entity.ShoppingItem
import com.example.supermarketlist.viewmodel.ShoppingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ShoppingViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableLongStateOf(-1L) }

    var selectedItemForAction by remember { mutableStateOf<ShoppingItem?>(null) }
    var showItemActionDialog by remember { mutableStateOf(false) }
    var showEditItemDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supermarket List") },
                actions = {
                    IconButton(onClick = onNavigateToCategories) {
                        Icon(Icons.Default.List, contentDescription = "Manage Categories")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = onNavigateToCamera,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = "Add by Photo")
                }
                SmallFloatingActionButton(
                    onClick = onStartVoiceInput,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Add by Voice")
                }
                FloatingActionButton(onClick = { showAddItemDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        }
    ) { padding ->
        if (categories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Please create a category first", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onNavigateToCategories) {
                        Text("Manage Categories")
                    }
                }
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Your list is empty", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                val grouped = items.groupBy { item ->
                    categories.find { it.id == item.categoryId }?.name ?: "Uncategorized"
                }

                grouped.forEach { (categoryName, categoryItems) ->
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
                    items(categoryItems) { item ->
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
            AlertDialog(
                onDismissRequest = { showAddItemDialog = false },
                title = { Text("Add Item") },
                text = {
                    Column {
                        TextField(
                            value = newItemName,
                            onValueChange = { newItemName = it },
                            label = { Text("Item Name") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Category:")
                        CategoryDropdown(
                            categories = categories,
                            selectedCategoryId = selectedCategoryId,
                            onCategorySelected = { selectedCategoryId = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newItemName.isNotBlank() && selectedCategoryId != -1L) {
                            viewModel.addItem(newItemName, selectedCategoryId)
                            newItemName = ""
                            showAddItemDialog = false
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddItemDialog = false }) {
                        Text("Cancel")
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
                                Icon(Icons.Default.Edit, contentDescription = null)
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
                                Icon(Icons.Default.Delete, contentDescription = null)
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
            var editName by remember { mutableStateOf(selectedItemForAction!!.name) }
            var editCategoryId by remember { mutableLongStateOf(selectedItemForAction!!.categoryId) }

            AlertDialog(
                onDismissRequest = { showEditItemDialog = false },
                title = { Text("Edit Item") },
                text = {
                    Column {
                        TextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Item Name") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select Category:")
                        CategoryDropdown(
                            categories = categories,
                            selectedCategoryId = editCategoryId,
                            onCategorySelected = { editCategoryId = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateItem(selectedItemForAction!!.copy(name = editName, categoryId = editCategoryId))
                            showEditItemDialog = false
                            selectedItemForAction = null
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditItemDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
fun CategoryDropdown(
    categories: List<Category>,
    selectedCategoryId: Long,
    onCategorySelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedCategory?.name ?: "Select Category")
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
        }
    }
}
