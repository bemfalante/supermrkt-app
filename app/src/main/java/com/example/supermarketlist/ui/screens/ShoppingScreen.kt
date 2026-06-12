package com.example.supermarketlist.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import java.text.Collator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    categoryId: Long?,
    onFinished: () -> Unit,
    onStartVoiceInput: () -> Unit
) {
    val categoryItemsFlow = viewModel.getItemsByCategory(categoryId).collectAsState(initial = emptyList())
    val categoryItems = categoryItemsFlow.value
    val activeShoppingItems by viewModel.activeShoppingItems.collectAsState()
    val itemsById = categoryItems.associateBy { it.id }

    val activeItems = activeShoppingItems.filter { itemsById.containsKey(it.itemId) }

    var showPriceQtyDialogForItem by remember { mutableStateOf<Long?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showPriceHistoryDialog by remember { mutableStateOf<String?>(null) }
    var showPaymentPrompt by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var showRenameCategoryDialog by remember { mutableStateOf(false) }

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "R$ ${String.format(ptBr, "%.2f", totalPrice)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (categoryId != null) {
                                IconButton(onClick = { showRenameCategoryDialog = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Rename Category",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { showCancelConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.scale(0.5775f), // 70% of 0.825f
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = !finishing
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        Button(
                            onClick = {
                                if (!finishing) {
                                    if (totalPrice > 0) {
                                        showPaymentPrompt = true
                                    } else {
                                        finishing = true
                                        scope.launch {
                                            viewModel.finishShopping("NONE")
                                            onFinished()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5DF4D)),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.scale(0.77f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            enabled = !finishing
                        ) {
                            Text("Finish Shopping!", color = Color.Black)
                        }
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
                else {
                    val collator = Collator.getInstance(Locale("pt", "BR")).apply {
                        strength = Collator.PRIMARY
                    }
                    collator.compare(nameA, nameB)
                }
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
                                // Removed white "x" from background as well
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
                                onPriceHistoryClick = {
                                    showPriceHistoryDialog = item.name
                                },
                                onPriceClick = {
                                    showPriceQtyDialogForItem = item.id
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
                var qtyInputs by remember(itemId) { mutableStateOf(listOf("")) }

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
                            Text("Quantities:", style = MaterialTheme.typography.bodySmall)
                            qtyInputs.forEachIndexed { index, qtyValue ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    TextField(
                                        value = qtyValue,
                                        onValueChange = { newValue ->
                                            qtyInputs = qtyInputs.toMutableList().apply { this[index] = newValue }
                                        },
                                        label = { Text("Qty ${index + 1}") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (qtyInputs.size > 1) {
                                        IconButton(onClick = {
                                            qtyInputs = qtyInputs.toMutableList().apply { removeAt(index) }
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove Qty", tint = Color.Red)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            TextButton(onClick = {
                                qtyInputs = qtyInputs + "1"
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Quantity Field")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val price = priceInput.replace(",", ".").toDoubleOrNull() ?: 0.0
                            val totalQty = qtyInputs.sumOf { it.replace(",", ".").toDoubleOrNull() ?: 0.0 }
                            val finalQty = if (totalQty > 0) totalQty else 1.0
                            viewModel.updateActiveItemDetails(itemId, price, finalQty)
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

        if (showRenameCategoryDialog && categoryId != null) {
            var newName by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }

            AlertDialog(
                onDismissRequest = { showRenameCategoryDialog = false },
                title = { Text("Set Category for this Session") },
                text = {
                    Column {
                        Text("This will create a new category and keep the original one intact.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Category Name") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.branchCategory(categoryId, newName)
                            showRenameCategoryDialog = false
                        }
                    }) {
                        Text("Save as New")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameCategoryDialog = false }) {
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
                text = { Text("Are you sure you want to cancel the current shopping session? All progress will be lost.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            viewModel.cancelShopping()
                            showCancelConfirmDialog = false
                            onFinished()
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    state: String,
    price: Double?,
    quantity: Double?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    onPriceHistoryClick: () -> Unit,
    onPriceClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onToggle() },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onPriceHistoryClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Price History",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (price != null) {
            val total = price * (quantity ?: 1.0)
            val ptBr = Locale("pt", "BR")
            Text(
                text = "R$ ${String.format(ptBr, "%.2f", total)}",
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .combinedClickable(
                        onClick = onPriceClick
                    ),
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
            // No white "x" icon inside the circle as per request
        }
    }
}
