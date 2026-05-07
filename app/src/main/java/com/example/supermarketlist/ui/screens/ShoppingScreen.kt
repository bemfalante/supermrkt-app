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
import androidx.compose.material.icons.filled.ShoppingCart
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

enum class ItemState { EMPTY, GREEN, RED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    categoryId: Long?,
    onFinished: () -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val categoryItems by viewModel.getItemsByCategory(categoryId).collectAsState(initial = emptyList())
    val activeItems = categoryItems.filter { !it.isChecked }

    val itemStates = remember { mutableStateMapOf<Long, ItemState>() }
    val itemPrices = remember { mutableStateMapOf<Long, Double>() }
    val itemQuantities = remember { mutableStateMapOf<Long, Double>() }

    var showPriceQtyDialogForItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                actions = {
                    Button(
                        onClick = {
                            val bought = activeItems.filter { itemStates[it.id] == ItemState.GREEN }
                                .map { Triple(it, itemPrices[it.id] ?: 0.0, itemQuantities[it.id] ?: 1.0) }
                            val foundNotBought = activeItems.filter { itemStates[it.id] == ItemState.RED }
                                .map { Triple(it, itemPrices[it.id] ?: 0.0, itemQuantities[it.id] ?: 1.0) }
                            val notFound = activeItems.filter { itemStates[it.id] == null || itemStates[it.id] == ItemState.EMPTY }

                            viewModel.finishShopping(categoryId, bought, foundNotBought, notFound)
                            onFinished()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
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
            val sortedItems = activeItems.sortedBy {
                val state = itemStates[it.id] ?: ItemState.EMPTY
                state != ItemState.EMPTY
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 80.dp)) {
                items(sortedItems, key = { it.id }) { item ->
                    ShoppingItemRow(
                        item = item,
                        state = itemStates[item.id] ?: ItemState.EMPTY,
                        price = itemPrices[item.id],
                        quantity = itemQuantities[item.id],
                        onToggle = {
                            val currentState = itemStates[item.id] ?: ItemState.EMPTY
                            if (currentState == ItemState.GREEN) {
                                itemStates[item.id] = ItemState.EMPTY
                                itemPrices.remove(item.id)
                                itemQuantities.remove(item.id)
                            } else {
                                showPriceQtyDialogForItem = item
                            }
                        },
                        onLongPress = {
                            itemStates[item.id] = ItemState.RED
                            showPriceQtyDialogForItem = item
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

                        if (itemStates[item.id] == ItemState.EMPTY || itemStates[item.id] == null) {
                             itemStates[item.id] = ItemState.GREEN
                        }

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
            MultiCategoryAddEditDialog(
                title = "Add Item",
                initialName = "",
                initialCategoryIds = if (categoryId != null) listOf(categoryId) else emptyList(),
                categories = categories,
                onDismiss = { showAddItemDialog = false },
                onConfirm = { name, catIds ->
                    viewModel.addItem(name, catIds)
                    showAddItemDialog = false
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

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    state: ItemState,
    price: Double?,
    quantity: Double?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onPriceLongPress: () -> Unit,
    onItemLongPressAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onItemLongPressAction() })
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
                        onLongPress = { onLongPress() }
                    )
                }
        )
    }
}
