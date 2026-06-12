package com.example.supermarketlist.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.supermarketlist.data.local.database.ShoppingDatabase
import com.example.supermarketlist.data.local.entity.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ShoppingDatabase.getDatabase(application).shoppingDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<ShoppingItemWithCategoryIds>> = dao.getAllItems()
        .flatMapLatest { itemList ->
            val flows = itemList.map { item ->
                dao.getCategoryIdsForItem(item.id).map { catIds ->
                    ShoppingItemWithCategoryIds(item, catIds)
                }
            }
            if (flows.isEmpty()) flowOf(emptyList())
            else combine(flows) { it.toList() }
        }
        .map { list ->
            val collator = Collator.getInstance(Locale("pt", "BR")).apply {
                strength = Collator.PRIMARY
            }
            list.sortedWith { a, b ->
                val checkedComp = a.item.isChecked.compareTo(b.item.isChecked)
                if (checkedComp != 0) checkedComp
                else collator.compare(a.item.name, b.item.name)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSession = MutableStateFlow<ActiveShoppingSession?>(null)
    val activeSession: StateFlow<ActiveShoppingSession?> = _activeSession.asStateFlow()

    val activeShoppingItems: StateFlow<List<ActiveShoppingItem>> = dao.getActiveShoppingItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            dao.getActiveSession().collect {
                _activeSession.value = it
            }
        }
    }

    val categories: StateFlow<List<Category>> = dao.getAllCategories()
        .map { list ->
            val collator = Collator.getInstance(Locale("pt", "BR")).apply {
                strength = Collator.PRIMARY
            }
            // Sort by displayOrder first, then name
            list.sortedWith { a, b ->
                val orderComp = a.displayOrder.compareTo(b.displayOrder)
                if (orderComp != 0) orderComp
                else collator.compare(a.name, b.name)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessions: StateFlow<List<ShoppingSession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _newItemAddedEvent = MutableSharedFlow<Long>()
    val newItemAddedEvent = _newItemAddedEvent.asSharedFlow()

    var lastUsedCategoryId by mutableStateOf<Long?>(null)
        private set

    fun addItem(name: String, categoryIds: List<Long>, onAdded: (Long) -> Unit = {}) {
        if (categoryIds.isNotEmpty()) {
            lastUsedCategoryId = categoryIds.last()
        }
        viewModelScope.launch {
            val id = dao.insertItem(ShoppingItem(name = name))
            categoryIds.forEach { catId ->
                dao.insertItemCategoryCrossRef(ItemCategoryCrossRef(itemId = id, categoryId = catId))
            }

            // If we are currently in a shopping session and the new item matches the category
            activeSession.value?.let { session ->
                if (session.categoryId == null && categoryIds.isEmpty()) {
                    dao.upsertActiveShoppingItem(ActiveShoppingItem(itemId = id, state = "GREEN", price = 0.0, quantity = 1.0))
                    _newItemAddedEvent.emit(id)
                } else if (session.categoryId != null && categoryIds.contains(session.categoryId)) {
                    dao.upsertActiveShoppingItem(ActiveShoppingItem(itemId = id, state = "GREEN", price = 0.0, quantity = 1.0))
                    _newItemAddedEvent.emit(id)
                }
            }
            onAdded(id)
        }
    }

    fun updateItem(item: ShoppingItem, categoryIds: List<Long>) {
        viewModelScope.launch {
            dao.updateItem(item)
            dao.deleteItemCategoryCrossRefs(item.id)
            categoryIds.forEach { catId ->
                dao.insertItemCategoryCrossRef(ItemCategoryCrossRef(itemId = item.id, categoryId = catId))
            }
        }
    }

    suspend fun getCategoryIdsForItem(itemId: Long): List<Long> {
        return dao.getCategoryIdsForItem(itemId).first()
    }

    fun toggleItem(item: ShoppingItem) {
        viewModelScope.launch {
            dao.updateItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            dao.deleteItem(item)
        }
    }

    fun addCategory(name: String, onComplete: (Long) -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return

        viewModelScope.launch {
            val existing = categories.value.any { it.name.trim().replace("\\s+".toRegex(), " ").equals(trimmedName.replace("\\s+".toRegex(), " "), ignoreCase = true) }
            if (existing) {
                Toast.makeText(getApplication(), "Category '$trimmedName' already exists", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val id = dao.insertCategory(Category(name = trimmedName, displayOrder = categories.value.size))
            onComplete(id)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            dao.deleteCategory(category)
        }
    }

    fun branchCategory(oldCategoryId: Long, newName: String) {
        viewModelScope.launch {
            val trimmedName = newName.trim()
            if (trimmedName.isNotBlank()) {
                // 1. Create the new category
                val newCategoryId = dao.insertCategory(Category(name = trimmedName, displayOrder = categories.value.size))

                // 2. Get items currently in this shopping session
                val activeItems = activeShoppingItems.value

                // 3. Associate these items with the new category
                activeItems.forEach { active ->
                    dao.insertItemCategoryCrossRef(ItemCategoryCrossRef(itemId = active.itemId, categoryId = newCategoryId))
                }

                // 4. Update the active session to point to the new category
                dao.setActiveSession(ActiveShoppingSession(categoryId = newCategoryId))

                // 5. Update local state
                _activeSession.value = ActiveShoppingSession(categoryId = newCategoryId)
            }
        }
    }

    fun updateCategoryOrder(orderedCategories: List<Category>) {
        viewModelScope.launch {
            orderedCategories.forEachIndexed { index, category ->
                dao.updateCategory(category.copy(displayOrder = index))
            }
        }
    }

    fun getItemsByCategory(categoryId: Long?): Flow<List<ShoppingItem>> {
        return if (categoryId == null) {
            dao.getUncategorizedItems()
        } else {
            dao.getItemsByCategory(categoryId)
        }
    }

    // Shopping Session Logic
    private var isStartingSession = false
    fun startShopping(categoryId: Long?) {
        if (isStartingSession || activeSession.value != null) return
        isStartingSession = true
        viewModelScope.launch {
            // 1. Set active session
            val session = ActiveShoppingSession(categoryId = categoryId)
            dao.setActiveSession(session)

            // 2. Pre-populate active shopping items from the selected items
            val currentItems = if (categoryId == null) {
                dao.getUncategorizedItems().first()
            } else {
                dao.getItemsByCategory(categoryId).first()
            }.filter { !it.isChecked }

            val activeItems = currentItems.map {
                ActiveShoppingItem(itemId = it.id, state = "EMPTY", price = 0.0, quantity = 1.0)
            }
            dao.insertActiveShoppingItems(activeItems)

            _activeSession.value = session
            isStartingSession = false
        }
    }

    fun updateActiveItemState(itemId: Long, state: String) {
        viewModelScope.launch {
            val current = activeShoppingItems.value.find { it.itemId == itemId }
                ?: ActiveShoppingItem(itemId = itemId, state = state, price = 0.0, quantity = 1.0)
            dao.upsertActiveShoppingItem(current.copy(state = state))
        }
    }

    fun updateActiveItemDetails(itemId: Long, price: Double, quantity: Double) {
        viewModelScope.launch {
            dao.updateActiveItemDetails(itemId, price, quantity)
        }
    }

    fun resetActiveItem(itemId: Long) {
        viewModelScope.launch {
            dao.upsertActiveShoppingItem(
                ActiveShoppingItem(itemId = itemId, state = "EMPTY", price = 0.0, quantity = 1.0)
            )
        }
    }

    private val finishMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun finishShopping(paymentMethod: String) {
        if (!finishMutex.tryLock()) return
        try {
            val currentSession = activeSession.value ?: return
            val categoryId = currentSession.categoryId

            // IMPORTANT: Immediately null out the local state to prevent UI re-navigation
            _activeSession.value = null

            val categoryName = if (categoryId == null) "Uncategorized"
            else categories.value.find { it.id == categoryId }?.name ?: "Unknown"

            val activeItems = dao.getActiveShoppingItemsSnapshot()
            val shoppingItems = dao.getAllItemsSnapshot().associateBy { it.id }

            val sessionItems = mutableListOf<ShoppingSessionItem>()
            val priceHistories = mutableListOf<ItemPriceHistory>()
            val itemsToMarkChecked = mutableListOf<ShoppingItem>()

            val totalPrice = activeItems.filter { it.state == "GREEN" }.sumOf { it.price * it.quantity }

            activeItems.forEach { active ->
                val item = shoppingItems[active.itemId] ?: return@forEach
                val status = when(active.state) {
                    "GREEN" -> "BOUGHT"
                    "RED" -> "FOUND_NOT_BOUGHT"
                    "NOT_FOUND_X" -> "NOT_FOUND"
                    else -> "NOT_FOUND"
                }

                sessionItems.add(
                    ShoppingSessionItem(
                        sessionId = 0, // Will be set in transaction
                        itemName = item.name,
                        price = active.price,
                        quantity = active.quantity,
                        status = status
                    )
                )

                if (status == "BOUGHT" || status == "FOUND_NOT_BOUGHT") {
                    priceHistories.add(
                        ItemPriceHistory(
                            itemName = item.name,
                            price = active.price,
                            quantity = active.quantity,
                            categoryName = categoryName,
                            status = status
                        )
                    )
                }

                if (status == "BOUGHT") {
                    itemsToMarkChecked.add(item)
                }
            }

            dao.completeShoppingSession(
                session = if (totalPrice > 0) ShoppingSession(categoryName = categoryName, paymentMethod = paymentMethod) else null,
                sessionItems = sessionItems,
                priceHistories = priceHistories,
                itemsToMarkChecked = itemsToMarkChecked
            )
        } finally {
            finishMutex.unlock()
        }
    }

    fun addManualSession(categoryName: String, paymentMethod: String, items: List<ShoppingSessionItem>) {
        viewModelScope.launch {
            val sessionId = dao.insertShoppingSession(ShoppingSession(categoryName = categoryName, paymentMethod = paymentMethod))
            items.forEach { item ->
                dao.insertShoppingSessionItem(item.copy(sessionId = sessionId))
                if (item.status == "BOUGHT" || item.status == "FOUND_NOT_BOUGHT") {
                    dao.insertPriceHistory(
                        ItemPriceHistory(
                            itemName = item.itemName,
                            price = item.price,
                            quantity = item.quantity,
                            categoryName = categoryName,
                            status = item.status
                        )
                    )
                }
            }
        }
    }

    fun updateShoppingSessionItem(item: ShoppingSessionItem) {
        viewModelScope.launch {
            dao.updateShoppingSessionItem(item)
        }
    }

    fun updateShoppingSessionPayment(sessionId: Long, paymentMethod: String) {
        viewModelScope.launch {
            dao.updateShoppingSessionPayment(sessionId, paymentMethod)
        }
    }

    fun deleteSession(session: ShoppingSession) {
        viewModelScope.launch {
            dao.deleteItemsForSession(session.id)
            dao.deleteSession(session)
        }
    }

    suspend fun cancelShopping() {
        dao.clearActiveShoppingItems()
        dao.clearActiveSession()
        _activeSession.value = null
    }

    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingSessionItem>> = dao.getItemsForSession(sessionId)

    fun getPriceHistoryForItem(itemName: String): Flow<List<ItemPriceHistory>> = dao.getPriceHistoryForItem(itemName)
}
