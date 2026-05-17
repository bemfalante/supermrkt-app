package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.data.local.entity.ShoppingSessionItem
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: ShoppingViewModel, onNavigateBack: () -> Unit) {
    val sessions by viewModel.sessions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    var showAddSessionDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingSessionItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSessionDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Shopping Session")
            }
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No shopping history yet")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sessions) { session ->
                    val sessionItems by viewModel.getItemsForSession(session.id).collectAsState(initial = emptyList())
                    val totalSessionPrice = sessionItems.filter { it.status == "BOUGHT" }.sumOf { it.price * it.quantity }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Shopping in ${session.categoryName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val ptBr = Locale("pt", "BR")
                                    Text(
                                        text = "Total: R$ ${String.format(ptBr, "%.2f", totalSessionPrice)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteSession(session) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = Color.Red)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dateFormat.format(Date(session.timestamp)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Paid via: ${session.paymentMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val bought = sessionItems.filter { it.status == "BOUGHT" }
                            val foundNotBought = sessionItems.filter { it.status == "FOUND_NOT_BOUGHT" }
                            val notFound = sessionItems.filter { it.status == "NOT_FOUND" || it.status == "NOT_FOUND_X" }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Payment: ", style = MaterialTheme.typography.bodySmall)
                                AssistChip(
                                    onClick = {
                                        val next = if (session.paymentMethod == "MONEY") "CARD" else "MONEY"
                                        viewModel.updateShoppingSessionPayment(session.id, next)
                                    },
                                    label = { Text(session.paymentMethod) }
                                )
                            }

                            if (bought.isNotEmpty()) {
                                Text("Bought:", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                                bought.forEach { item ->
                                    HistoryItemRow(item, onEdit = { editingItem = item })
                                }
                            }

                            if (foundNotBought.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Found but not bought:", style = MaterialTheme.typography.labelMedium, color = Color.Red)
                                foundNotBought.forEach { item ->
                                    HistoryItemRow(item, onEdit = { editingItem = item })
                                }
                            }

                            if (notFound.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Not found:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                notFound.forEach { item ->
                                    HistoryItemRow(item, onEdit = { editingItem = item })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSessionDialog) {
            AddManualSessionDialog(
                categories = categories,
                onDismiss = { showAddSessionDialog = false },
                onConfirm = { categoryName, paymentMethod, items ->
                    viewModel.addManualSession(categoryName, paymentMethod, items)
                    showAddSessionDialog = false
                }
            )
        }

        if (editingItem != null) {
            EditHistoryItemDialog(
                item = editingItem!!,
                onDismiss = { editingItem = null },
                onConfirm = { updatedItem ->
                    viewModel.updateShoppingSessionItem(updatedItem)
                    editingItem = null
                }
            )
        }
    }
}

@Composable
fun HistoryItemRow(item: ShoppingSessionItem, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val ptBr = Locale("pt", "BR")
        Text(
            text = "• ${item.itemName} (Qty: ${item.quantity}) - R$ ${String.format(ptBr, "%.2f", item.price * item.quantity)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun EditHistoryItemDialog(
    item: ShoppingSessionItem,
    onDismiss: () -> Unit,
    onConfirm: (ShoppingSessionItem) -> Unit
) {
    var name by remember { mutableStateOf(item.itemName) }
    var price by remember { mutableStateOf(item.price.toString()) }
    var qty by remember { mutableStateOf(item.quantity.toString()) }
    var status by remember { mutableStateOf(item.status) }

    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit History Item") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = status == "BOUGHT", onClick = { status = "BOUGHT" })
                    Text("Bought")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = status == "FOUND_NOT_BOUGHT", onClick = { status = "FOUND_NOT_BOUGHT" })
                    Text("Found but not bought")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onConfirm(
                        item.copy(
                            itemName = name,
                            price = price.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            quantity = qty.replace(",", ".").toDoubleOrNull() ?: 1.0,
                            status = status
                        )
                    )
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
fun AddManualSessionDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<ShoppingSessionItem>) -> Unit
) {
    var selectedCategoryName by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("MONEY") }
    val sessionItems = remember { mutableStateListOf<ShoppingSessionItem>() }
    var step by remember { mutableIntStateOf(1) } // 1: Category, 2: Items, 3: Payment

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select Category") },
            text = {
                Column {
                    var categoryNameInput by remember { mutableStateOf("") }
                    val focusRequester = remember { FocusRequester() }

                    TextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        label = { Text("Category Name") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        items(categories) { category ->
                            TextButton(
                                onClick = { categoryNameInput = category.name },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(category.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        item {
                            TextButton(
                                onClick = { categoryNameInput = "Uncategorized" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Uncategorized", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        delay(100)
                        focusRequester.requestFocus()
                    }

                    selectedCategoryName = categoryNameInput
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedCategoryName.isNotBlank()) {
                        selectedCategoryName = selectedCategoryName.trim()
                        step = 2
                    }
                }) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    } else if (step == 2) {
        var showAddItemMenu by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Items in $selectedCategoryName") },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(sessionItems) { item ->
                            val ptBr = Locale("pt", "BR")
                            Text("• ${item.itemName} - ${item.quantity} x R$ ${String.format(ptBr, "%.2f", item.price)} (${item.status})")
                        }
                    }
                    Button(onClick = { showAddItemMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add Item")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sessionItems.isNotEmpty()) step = 3
                }) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { step = 1 }) {
                    Text("Back")
                }
            }
        )

        if (showAddItemMenu) {
            ManualItemEntryMenu(
                onDismiss = { showAddItemMenu = false },
                onAdd = { item ->
                    sessionItems.add(item)
                    showAddItemMenu = false
                }
            )
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Payment Method") },
            text = {
                Column {
                    Text("Select how you paid for these items:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = paymentMethod == "MONEY", onClick = { paymentMethod = "MONEY" })
                        Text("Money")
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = paymentMethod == "CARD", onClick = { paymentMethod = "CARD" })
                        Text("Card")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(selectedCategoryName, paymentMethod, sessionItems)
                }) {
                    Text("Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { step = 2 }) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
fun ManualItemEntryMenu(
    onDismiss: () -> Unit,
    onAdd: (ShoppingSessionItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var status by remember { mutableStateOf("BOUGHT") }

    val nameFocusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Item Details") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = qty,
                    onValueChange = { qty = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = status == "BOUGHT", onClick = { status = "BOUGHT" })
                    Text("Bought")
                    Spacer(modifier = Modifier.width(8.dp))
                    RadioButton(selected = status == "FOUND_NOT_BOUGHT", onClick = { status = "FOUND_NOT_BOUGHT" })
                    Text("Found but not bought")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onAdd(
                        ShoppingSessionItem(
                            sessionId = 0, // Placeholder
                            itemName = name,
                            price = price.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            quantity = qty.replace(",", ".").toDoubleOrNull() ?: 1.0,
                            status = status
                        )
                    )
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    LaunchedEffect(Unit) {
        delay(100)
        nameFocusRequester.requestFocus()
    }
}
