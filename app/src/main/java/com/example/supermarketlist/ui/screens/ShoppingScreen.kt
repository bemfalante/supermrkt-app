package com.example.supermarketlist.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    onNavigateBack: () -> Unit
) {
    val items by viewModel.items.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val filteredItems = items.filter { it.categoryId == categoryId && !it.isChecked }

    val itemStates = remember { mutableStateMapOf<Long, ItemState>() }
    val itemPrices = remember { mutableStateMapOf<Long, Double>() }

    var showPriceDialogForItem by remember { mutableStateOf<ShoppingItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shopping") },
                actions = {
                    Button(
                        onClick = {
                            val purchased = filteredItems.filter { itemStates[it.id] == ItemState.GREEN }
                                .map { it to (itemPrices[it.id] ?: 0.0) }
                            viewModel.finishShopping(purchased, categories)
                            onFinished()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Finish Shopping!", color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        if (filteredItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No active items in this category")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(filteredItems) { item ->
                    ShoppingItemRow(
                        item = item,
                        state = itemStates[item.id] ?: ItemState.EMPTY,
                        price = itemPrices[item.id],
                        onToggle = {
                            val currentState = itemStates[item.id] ?: ItemState.EMPTY
                            if (currentState == ItemState.GREEN) {
                                itemStates[item.id] = ItemState.EMPTY
                                itemPrices.remove(item.id)
                            } else {
                                showPriceDialogForItem = item
                            }
                        },
                        onLongPress = {
                            itemStates[item.id] = ItemState.RED
                            itemPrices.remove(item.id)
                        }
                    )
                }
            }
        }

        showPriceDialogForItem?.let { item ->
            var priceInput by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }

            AlertDialog(
                onDismissRequest = { showPriceDialogForItem = null },
                title = { Text("Item Price") },
                text = {
                    TextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val price = priceInput.toDoubleOrNull() ?: 0.0
                        itemStates[item.id] = ItemState.GREEN
                        itemPrices[item.id] = price
                        showPriceDialogForItem = null
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPriceDialogForItem = null }) {
                        Text("Cancel")
                    }
                }
            )

            LaunchedEffect(Unit) {
                delay(100)
                focusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    state: ItemState,
    price: Double?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)

        if (price != null) {
            Text(
                text = String.format("%.2f", price),
                modifier = Modifier.padding(horizontal = 8.dp),
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        ItemState.EMPTY -> Color.LightGray.copy(alpha = 0.3f)
                        ItemState.GREEN -> Color.Green
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
