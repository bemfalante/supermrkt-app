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

enum class ItemState { EMPTY, GREEN, RED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    categoryId: Long?,
    onFinished: () -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val categoryItems by viewModel.getItemsByCategory(categoryId).collectAsState(initial = emptyList())
    val activeShoppingItems by viewModel.activeShoppingItems.collectAsState()
    val itemsById = categoryItems.associateBy { it.id }

    // We only show items that are in the active session and match the current category filter
    val activeItems = activeShoppingItems.filter { itemsById.containsKey(it.itemId) }

    var showPriceQtyDialogForItem by remember { mutableStateOf<Long?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }

    var finishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val ptBr = remember { Locale("pt", "BR") }
    val totalPrice = activeItems.filter { it.state == "GREEN" }.sumOf { it.price * it.quantity }

    // Never close except via Finish button
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
                    Button(
                        onClick = {
                            if (!finishing) {
                                finishing = true
                                scope.launch {
                                    viewModel.finishShopping()
                                    onFinished()
                                }
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
            // Sort: GREEN at very bottom, RED above them, EMPTY at top.
            val sortedItems = activeItems.sortedWith { a, b ->
                val stateA = a.state
                val stateB = b.state

                val weightA = when(stateA) {
                    "EMPTY" -> 0
                    "RED" -> 1
                    "GREEN" -> 2
                    else -> 0
                }
                val weightB = when(stateB) {
                    "EMPTY" -> 0
                    "RED" -> 1
                    "GREEN" -> 2
                    else -> 0
                }
                weightA.compareTo(weightB)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 80.dp)) {
                items(sortedItems, key = { it.itemId }) { activeItem ->
                    val item = itemsById[activeItem.itemId] ?: return@items
                    ShoppingItemRow(
                        item = item,
                        state = when(activeItem.state) {
                            "GREEN" -> ItemState.GREEN
                            "RED" -> ItemState.RED
                            else -> ItemState.EMPTY
                        },
                        price = if (activeItem.price > 0) activeItem.price else null,
                        quantity = activeItem.quantity,
                        onToggle = {
                            // Single Click -> BOUGHT (Green)
                            viewModel.updateActiveItemState(item.id, "GREEN")
                            showPriceQtyDialogForItem = item.id
                        },
                        onLongPress = {
                            // Long Press -> FOUND NOT BOUGHT (Red)
                            viewModel.updateActiveItemState(item.id, "RED")
                            showPriceQtyDialogForItem = item.id
                        },
                        onDoubleClick = {
                            // Double Click -> NOT FOUND (Empty)
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
                            viewModel.addItem(newItemName, categoryId)
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
    state: ItemState,
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
                        ItemState.EMPTY -> Color.LightGray.copy(alpha = 0.3f)
                        ItemState.GREEN -> Color(0xFF4CAF50)
                        ItemState.RED -> Color.Red
                    }
                )
        )
    }
}
