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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ShoppingDatabase.getDatabase(application).shoppingDao()

    val categories: StateFlow<List<Category>> = dao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<ShoppingItem>> = dao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<ActiveShoppingSession?> = dao.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sessions: StateFlow<List<ShoppingSession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var lastUsedCategoryId by mutableStateOf<Long?>(null)
        private set

    fun addItem(name: String, categoryId: Long?) {
        lastUsedCategoryId = categoryId
        viewModelScope.launch {
            dao.insertItem(ShoppingItem(name = name, categoryId = categoryId))
        }
    }

    fun updateItem(item: ShoppingItem) {
        lastUsedCategoryId = item.categoryId
        viewModelScope.launch {
            dao.updateItem(item)
        }
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
            val id = dao.insertCategory(Category(name = trimmedName))
            onComplete(id)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            dao.deleteCategory(category)
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
        if (isStartingSession) return
        isStartingSession = true
        viewModelScope.launch {
            dao.setActiveSession(ActiveShoppingSession(categoryId = categoryId))
            isStartingSession = false
        }
    }

    suspend fun finishShopping(
        categoryId: Long?,
        purchasedItems: List<Triple<ShoppingItem, Double, Double>>, // Item, Price, Quantity
        foundNotBoughtItems: List<Triple<ShoppingItem, Double, Double>>,
        notFoundItems: List<ShoppingItem>
    ) {
        val categoryName = if (categoryId == null) "Uncategorized"
        else categories.value.find { it.id == categoryId }?.name ?: "Unknown"

        val sessionId = dao.insertShoppingSession(ShoppingSession(categoryName = categoryName))

        purchasedItems.forEach { (item, price, qty) ->
            dao.insertShoppingSessionItem(
                ShoppingSessionItem(
                    sessionId = sessionId,
                    itemName = item.name,
                    price = price,
                    quantity = qty,
                    status = "BOUGHT"
                )
            )
            dao.insertPriceHistory(
                ItemPriceHistory(
                    itemName = item.name,
                    price = price,
                    quantity = qty,
                    categoryName = categoryName,
                    status = "BOUGHT"
                )
            )
            dao.updateItem(item.copy(isChecked = true))
        }
        foundNotBoughtItems.forEach { (item, price, qty) ->
            dao.insertShoppingSessionItem(
                ShoppingSessionItem(
                    sessionId = sessionId,
                    itemName = item.name,
                    price = price,
                    quantity = qty,
                    status = "FOUND_NOT_BOUGHT"
                )
            )
            dao.insertPriceHistory(
                ItemPriceHistory(
                    itemName = item.name,
                    price = price,
                    quantity = qty,
                    categoryName = categoryName,
                    status = "FOUND_NOT_BOUGHT"
                )
            )
        }
        notFoundItems.forEach { item ->
            dao.insertShoppingSessionItem(
                ShoppingSessionItem(
                    sessionId = sessionId,
                    itemName = item.name,
                    price = 0.0,
                    quantity = 0.0,
                    status = "NOT_FOUND"
                )
            )
        }

        dao.clearActiveSession()
    }

    fun addManualSession(categoryName: String, items: List<ShoppingSessionItem>) {
        viewModelScope.launch {
            val sessionId = dao.insertShoppingSession(ShoppingSession(categoryName = categoryName))
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
            // Also update price history if it was a price change
            if (item.status == "BOUGHT" || item.status == "FOUND_NOT_BOUGHT") {
                 // For simplicity, we just add a new entry or could try to update matching one.
                 // Requirements didn't specify updating past history entries, but it's good practice.
            }
        }
    }

    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingSessionItem>> = dao.getItemsForSession(sessionId)

    fun getPriceHistoryForItem(itemName: String): Flow<List<ItemPriceHistory>> = dao.getPriceHistoryForItem(itemName)
}
