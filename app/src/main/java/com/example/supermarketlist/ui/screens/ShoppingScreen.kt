package com.example.supermarketlist.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.supermarketlist.R
import com.example.supermarketlist.data.local.entity.ShoppingItem
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    categoryId: Long?,
    onFinished: () -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val allItems by viewModel.items.collectAsState()
    val activeShoppingItems by viewModel.activeShoppingItems.collectAsState()

    // 1.10 fix: Ensure robust filtering for shopping session items
    val activeItems = remember(allItems, activeShoppingItems, categoryId) {
        if (allItems.isEmpty() || activeShoppingItems.isEmpty()) {
            emptyList()
        } else {
            val itemMap = allItems.associateBy { it.item.id }
            activeShoppingItems.filter { active ->
                val itemWithCats = itemMap[active.itemId]
                if (itemWithCats == null) return@filter false

                val catIds = itemWithCats.categories.map { it.id }
                if (categoryId == null) {
                    catIds.isEmpty()
                } else {
                    catIds.contains(categoryId)
                }
            }
        }
    }

    val itemsById = allItems.associate { it.item.id to it.item }

    var showPriceQtyDialogForItem by remember { mutableStateOf<Long?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }
    var showPaymentPrompt by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    var finishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val ptBr = remember { Locale("pt", "BR") }
    val totalPrice = activeItems.filter { it.state == "GREEN" }.sumOf { it.price * it.quantity }

    BackHandler {
        // Do nothing to prevent system back navigation
    }

    LaunchedEffect(Unit) {
        viewModel.newItemAddedEvent.collect { newId ->
            showPriceQtyDialogForItem = newId
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shopping")
                        Text(
                            text = "Total: R$ ${String.format(ptBr, "%.2f", totalPrice)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCancelConfirmDialog = true },
                        enabled = !finishing
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Red)
                    }

                    Button(
                        onClick = {
                            if (!finishing) {
                                showPaymentPrompt = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !finishing
                    ) {
                        Text("Finish Shopping!", color = Color.White)
                    }
                }
            )
        },
        floatingActionButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                 FloatingActionButton(
                    onClick = onStartVoiceInput,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_mic), contentDescription = "Add by Voice")
                }

                Spacer(modifier = Modifier.width(32.dp))

                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        if (activeItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active items in this category")
            }
        } else {
            val sortedItems = activeItems.sortedWith { a, b ->
                val nameA = itemsById[a.itemId]?.name ?: ""
                val nameB = itemsById[b.itemId]?.name ?: ""
                val stateA = a.state
                val stateB = b.state
                val weightA = when(stateA) {
                    "EMPTY" -> 0
                    "NOT_FOUND_X" -> 1
                    "RED" -> 2
                    "GREEN" -> 3
                    else -> 0
                }
                val weightB = when(stateB) {
                    "EMPTY" -> 0
                    "NOT_FOUND_X" -> 1
                    "RED" -> 2
                    "GREEN" -> 3
                    else -> 0
                }
                if (weightA != weightB) weightA.compareTo(weightB)
                else nameA.lowercase().compareTo(nameB.lowercase())
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 80.dp)) {
                items(sortedItems, key = { it.itemId }) { activeItem ->
                    val item = itemsById[activeItem.itemId] ?: return@items

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = {
                            if (it == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.updateActiveItemState(item.id, "NOT_FOUND_X")
                            }
                            false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) Color.Red else Color.Transparent
                            Box(
                                modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Not Found", tint = Color.White)
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        content = {
                            ShoppingItemRow(
                                item = item,
                                state = activeItem.state,
                                price = if (activeItem.price > 0) activeItem.price else null,
                                quantity = activeItem.quantity,
                                onToggle = {
                                    viewModel.updateActiveItemState(item.id, "GREEN")
                                    showPriceQtyDialogForItem = item.id
                                },
                                onLongPress = {
                                    viewModel.updateActiveItemState(item.id, "RED")
                                    showPriceQtyDialogForItem = item.id
                                },
                                onDoubleClick = {
                                    viewModel.resetActiveItem(item.id)
                                },
                                onPriceLongPress = {
                                     showPriceQtyDialogForItem = item.id
                                },
                                onItemLongPressAction = {
                                     showPriceHistoryDialog = item.name
                                }
                            )
                        }
                    )
                }
            }
        }

        if (showPriceQtyDialogForItem != null) {
            val itemId = showPriceQtyDialogForItem!!
            val item = itemsById[itemId]
            val activeItem = activeShoppingItems.find { it.itemId == itemId }
            val priceFocusRequester = remember { FocusRequester() }

            if (item != null) {
                var priceInput by remember(itemId) { mutableStateOf(if (activeItem != null && activeItem.price > 0) activeItem.price.toString() else "") }
                var qtyInput by remember(itemId) { mutableStateOf(activeItem?.quantity?.toString() ?: "1") }

                AlertDialog(
                    onDismissRequest = { showPriceQtyDialogForItem = null },
                    title = { Text("Details: ${item.name}") },
                    text = {
                        Column {
                            TextField(
                                value = priceInput,
                                onValueChange = { priceInput = it },
                                label = { Text("Price (R$)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().focusRequester(priceFocusRequester)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextField(
                                value = qtyInput,
                                onValueChange = { qtyInput = it },
                                label = { Text("Quantity") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val price = priceInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val qty = qtyInput.replace(",", ".").toDoubleOrNull() ?: 1.0
                            viewModel.updateActiveItemDetails(itemId, price, qty)
                            showPriceQtyDialogForItem = null
                        }) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPriceQtyDialogForItem = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
            LaunchedEffect(itemId) {
                delay(100)
                priceFocusRequester.requestFocus()
            }
        }

        if (showAddItemDialog) {
            var newItemName by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }

            AlertDialog(
                onDismissRequest = { showAddItemDialog = false },
                title = { Text("Add Item to this Category") },
                text = {
                    TextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newItemName.isNotBlank()) {
                            viewModel.addItem(newItemName, if (categoryId != null) listOf(categoryId) else emptyList())
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
            LaunchedEffect(Unit) {
                delay(100)
                focusRequester.requestFocus()
            }
        }

        if (showCancelConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCancelConfirmDialog = false },
                title = { Text("Cancel Shopping?") },
                text = { Text("All progress in this session will be lost.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cancelShopping()
                        showCancelConfirmDialog = false
                        onFinished()
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

        if (showPaymentPrompt) {
            AlertDialog(
                onDismissRequest = { showPaymentPrompt = false },
                title = { Text("Payment Method") },
                text = { Text("How did you pay for this shopping?") },
                confirmButton = {
                    Row {
                        Button(onClick = {
                            finishing = true
                            showPaymentPrompt = false
                            scope.launch {
                                viewModel.finishShopping("MONEY")
                                onFinished()
                            }
                        }) { Text("Money") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            finishing = true
                            showPaymentPrompt = false
                            scope.launch {
                                viewModel.finishShopping("CARD")
                                onFinished()
                            }
                        }) { Text("Card") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPaymentPrompt = false }) {
                        Text("Cancel")
                    }
                }
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

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    state: String,
    price: Double?,
    quantity: Double?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    onPriceLongPress: () -> Unit,
    onItemLongPressAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggle() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (price != null) {
            val total = price * (quantity ?: 1.0)
            val ptBr = Locale("pt", "BR")
            Text(
                text = "R$ ${String.format(ptBr, "%.2f", total)}",
                modifier = Modifier.padding(horizontal = 8.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        "EMPTY" -> Color.LightGray.copy(alpha = 0.3f)
                        "GREEN" -> Color(0xFF4CAF50)
                        "RED" -> Color.Red
                        "NOT_FOUND_X" -> Color.Red.copy(alpha = 0.5f)
                        else -> Color.LightGray.copy(alpha = 0.3f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (state == "NOT_FOUND_X") {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}
