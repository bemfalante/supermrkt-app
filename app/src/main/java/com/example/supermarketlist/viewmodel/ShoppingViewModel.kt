package com.example.supermarketlist.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.supermarketlist.data.local.database.ShoppingDatabase
import com.example.supermarketlist.data.local.entity.BoughtItem
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.data.local.entity.ShoppingItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ShoppingDatabase.getDatabase(application).shoppingDao()

    val categories: StateFlow<List<Category>> = dao.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<ShoppingItem>> = dao.getAllItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val boughtItems: StateFlow<List<BoughtItem>> = dao.getAllBoughtItems()
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
        viewModelScope.launch {
            val id = dao.insertCategory(Category(name = name))
            onComplete(id)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            dao.deleteCategory(category)
        }
    }

    fun finishShopping(purchasedItems: List<Pair<ShoppingItem, Double>>, categories: List<Category>) {
        viewModelScope.launch {
            purchasedItems.forEach { (item, price) ->
                val categoryName = categories.find { it.id == item.categoryId }?.name ?: "Uncategorized"
                dao.insertBoughtItem(
                    BoughtItem(
                        name = item.name,
                        price = price,
                        categoryName = categoryName
                    )
                )
                dao.deleteItem(item)
            }
        }
    }
}
