package com.example.supermarketlist.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.viewmodel.ShoppingViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(viewModel: ShoppingViewModel, onNavigateBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    var listData by remember { mutableStateOf(emptyList<Category>()) }

    LaunchedEffect(categories) {
        listData = categories
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(lazyListState) { fromIndex, toIndex ->
        listData = listData.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        viewModel.updateCategoryOrder(listData)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Text("+")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dragDropColumn(dragDropState),
            state = lazyListState
        ) {
            itemsIndexed(listData, key = { _, cat -> cat.id }) { index, category ->
                DraggableItem(dragDropState, index) { isDragging ->
                    val elevation by animateFloatAsState(if (isDragging) 8f else 0f)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .graphicsLayer { shadowElevation = elevation },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Drag Handle",
                                    modifier = Modifier.padding(end = 16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Text(category.name, style = MaterialTheme.typography.bodyLarge)
                            }
                            IconButton(onClick = { viewModel.deleteCategory(category) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Category") },
                text = {
                    TextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(newCategoryName)
                            newCategoryName = ""
                            showAddDialog = false
                        }
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(lazyListState, scope, onMove)
    }
    return state
}

class DragDropState(
    val lazyListState: LazyListState,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedItemIndex by mutableStateOf<Int?>(null)
        private set

    internal var itemOffset by mutableStateOf(0f)
        private set

    private var scrollJob: Job? = null

    fun onDragStart(offset: androidx.compose.ui.geometry.Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.let { item ->
                draggedItemIndex = item.index
            }
    }

    fun onDragInterrupted() {
        draggedItemIndex = null
        itemOffset = 0f
        scrollJob?.cancel()
    }

    fun onDrag(offset: androidx.compose.ui.geometry.Offset) {
        itemOffset += offset.y

        val draggedItem = draggedItemIndex?.let { index ->
            lazyListState.layoutInfo.visibleItemsInfo.find { it.index == index }
        } ?: return

        val startOffset = draggedItem.offset + itemOffset
        val endOffset = startOffset + draggedItem.size

        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                (item.index != draggedItemIndex) &&
                (if (item.index > draggedItemIndex!!) endOffset > (item.offset + item.size / 2)
                 else startOffset < (item.offset + item.size / 2))
            }
            ?.let { targetItem ->
                onMove(draggedItemIndex!!, targetItem.index)
                draggedItemIndex = targetItem.index
                itemOffset -= (targetItem.offset - draggedItem.offset)
            }

        checkForOverScroll()
    }

    private fun checkForOverScroll() {
        val draggedItem = draggedItemIndex?.let { index ->
            lazyListState.layoutInfo.visibleItemsInfo.find { it.index == index }
        } ?: return

        val startOffset = draggedItem.offset + itemOffset
        val endOffset = startOffset + draggedItem.size

        val viewportStart = lazyListState.layoutInfo.viewportStartOffset
        val viewportEnd = lazyListState.layoutInfo.viewportEndOffset

        val diff = when {
            startOffset < viewportStart -> startOffset - viewportStart
            endOffset > viewportEnd -> endOffset - viewportEnd
            else -> 0f
        }

        if (diff != 0f) {
            if (scrollJob?.isActive != true) {
                scrollJob = coroutineScope.launch {
                    lazyListState.scrollBy(diff)
                }
            }
        } else {
            scrollJob?.cancel()
        }
    }
}

fun Modifier.dragDropColumn(state: DragDropState): Modifier = this.pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> state.onDragStart(offset) },
        onDragEnd = { state.onDragInterrupted() },
        onDragCancel = { state.onDragInterrupted() },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount)
        }
    )
}

@Composable
fun DraggableItem(
    state: DragDropState,
    index: Int,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = state.draggedItemIndex == index
    val draggingModifier = if (isDragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.itemOffset
            }
    } else {
        Modifier.zIndex(0f)
    }

    Box(modifier = draggingModifier) {
        content(isDragging)
    }
}
