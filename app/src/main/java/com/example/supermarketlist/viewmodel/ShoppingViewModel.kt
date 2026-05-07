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

    var lastUsedCategoryIds by mutableStateOf<List<Long>>(emptyList())
        private set

    fun addItem(name: String, categoryIds: List<Long>) {
        lastUsedCategoryIds = categoryIds
        viewModelScope.launch {
            val itemId = dao.insertItem(ShoppingItem(name = name))
            categoryIds.forEach { catId ->
                dao.insertItemCategoryCrossRef(ItemCategoryCrossRef(itemId, catId))
            }
        }
    }

    fun updateItem(item: ShoppingItem, categoryIds: List<Long>) {
        lastUsedCategoryIds = categoryIds
        viewModelScope.launch {
            dao.updateItem(item)
            dao.deleteItemCategoryCrossRefs(item.id)
            categoryIds.forEach { catId ->
                dao.insertItemCategoryCrossRef(ItemCategoryCrossRef(item.id, catId))
            }
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
            dao.deleteItemCategoryCrossRefs(item.id)
        }
    }

    fun addCategory(name: String, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val existing = categories.value.any { it.name.equals(name, ignoreCase = true) }
            if (existing) {
                Toast.makeText(getApplication(), "Category '$name' already exists", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val id = dao.insertCategory(Category(name = name))
            onComplete(id)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            dao.deleteCategory(category)
        }
    }

    fun getCategoriesForItem(itemId: Long): Flow<List<Category>> = dao.getCategoriesForItem(itemId)

    fun getItemsByCategory(categoryId: Long?): Flow<List<ShoppingItem>> {
        return if (categoryId == null) {
            dao.getUncategorizedItems()
        } else {
            dao.getItemsByCategory(categoryId)
        }
    }

    // Shopping Session Logic
    fun startShopping(categoryId: Long?) {
        viewModelScope.launch {
            dao.setActiveSession(ActiveShoppingSession(categoryId = categoryId))
        }
    }

    fun finishShopping(
        categoryId: Long?,
        purchasedItems: List<Triple<ShoppingItem, Double, Double>>, // Item, Price, Quantity
        foundNotBoughtItems: List<Triple<ShoppingItem, Double, Double>>,
        notFoundItems: List<ShoppingItem>
    ) {
        viewModelScope.launch {
            val categoryName = if (categoryId == null) "Uncategorized"
                              else categories.value.find { it.id == categoryId }?.name ?: "Unknown"

            val sessionId = dao.insertShoppingSession(ShoppingSession(categoryName = categoryName))

            // Record everything in session items
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
                dao.insertPriceHistory(ItemPriceHistory(itemName = item.name, price = price, categoryName = categoryName, status = "BOUGHT"))
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
                dao.insertPriceHistory(ItemPriceHistory(itemName = item.name, price = price, categoryName = categoryName, status = "FOUND_NOT_BOUGHT"))
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
    }

    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingSessionItem>> = dao.getItemsForSession(sessionId)

    fun getPriceHistoryForItem(itemName: String): Flow<List<ItemPriceHistory>> = dao.getPriceHistoryForItem(itemName)
}
