package com.example.supermarketlist.ui.screens

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
    val activeItems = categoryItems.filter { !it.isChecked }

    val itemStates = remember { mutableStateMapOf<Long, ItemState>() }
    val itemPrices = remember { mutableStateMapOf<Long, Double>() }
    val itemQuantities = remember { mutableStateMapOf<Long, Double>() }

    var showPriceQtyDialogForItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }

    var finishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val totalPrice = remember(itemPrices, itemQuantities) {
        itemPrices.keys.filter { itemStates[it] == ItemState.GREEN }.sumOf {
            (itemPrices[it] ?: 0.0) * (itemQuantities[it] ?: 1.0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shopping")
                        Text(
                            text = "Total: R$ ${String.format("%.2f", totalPrice)}",
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
                                    val bought = activeItems.filter { itemStates[it.id] == ItemState.GREEN }
                                        .map { Triple(it, itemPrices[it.id] ?: 0.0, itemQuantities[it.id] ?: 1.0) }
                                    val foundNotBought = activeItems.filter { itemStates[it.id] == ItemState.RED }
                                        .map { Triple(it, itemPrices[it.id] ?: 0.0, itemQuantities[it.id] ?: 1.0) }
                                    val notFound = activeItems.filter { itemStates[it.id] == null || itemStates[it.id] == ItemState.EMPTY }

                                    viewModel.finishShopping(categoryId, bought, foundNotBought, notFound)
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
                val stateA = itemStates[a.id] ?: ItemState.EMPTY
                val stateB = itemStates[b.id] ?: ItemState.EMPTY

                val weightA = when(stateA) {
                    ItemState.EMPTY -> 0
                    ItemState.RED -> 1
                    ItemState.GREEN -> 2
                }
                val weightB = when(stateB) {
                    ItemState.EMPTY -> 0
                    ItemState.RED -> 1
                    ItemState.GREEN -> 2
                }
                weightA.compareTo(weightB)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 80.dp)) {
                items(sortedItems, key = { it.id }) { item ->
                    ShoppingItemRow(
                        item = item,
                        state = itemStates[item.id] ?: ItemState.EMPTY,
                        price = itemPrices[item.id],
                        quantity = itemQuantities[item.id],
                        onToggle = {
                            // Single Click -> BOUGHT (Green)
                            itemStates[item.id] = ItemState.GREEN
                            showPriceQtyDialogForItem = item
                        },
                        onLongPress = {
                            // Long Press -> FOUND NOT BOUGHT (Red)
                            itemStates[item.id] = ItemState.RED
                            showPriceQtyDialogForItem = item
                        },
                        onDoubleClick = {
                            // Double Click -> NOT FOUND (Empty)
                            itemStates[item.id] = ItemState.EMPTY
                            itemPrices.remove(item.id)
                            itemQuantities.remove(item.id)
                        },
                        onPriceLongPress = {
                             showPriceQtyDialogForItem = item
                        },
                        onItemLongPressAction = {
                             showPriceHistoryDialog = item.name
                        }
                    )
                }
            }
        }

        if (showPriceQtyDialogForItem != null) {
            val item = showPriceQtyDialogForItem!!
            var priceInput by remember { mutableStateOf(itemPrices[item.id]?.toString() ?: "") }
            var qtyInput by remember { mutableStateOf(itemQuantities[item.id]?.toString() ?: "1") }
            val priceFocusRequester = remember { FocusRequester() }

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

                        itemPrices[item.id] = price
                        itemQuantities[item.id] = qty
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

            LaunchedEffect(Unit) {
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
            Text(
                text = "R$ ${String.format("%.2f", total)}",
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onPriceLongPress() })
                    },
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onToggle() },
                        onLongPress = { onLongPress() },
                        onDoubleTap = { onDoubleClick() }
                    )
                }
        )
    }
}
