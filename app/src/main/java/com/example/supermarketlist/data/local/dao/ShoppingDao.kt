package com.example.supermarketlist.data.local.dao

import androidx.room.*
import com.example.supermarketlist.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    // Categories
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)

    // Items
    @Query("SELECT * FROM shopping_items")
    fun getAllItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItemsSnapshot(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE categoryId = :categoryId")
    fun getItemsByCategory(categoryId: Long): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE categoryId IS NULL")
    fun getUncategorizedItems(): Flow<List<ShoppingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem): Long

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)

    // Shopping Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSession(session: ShoppingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSessionItem(item: ShoppingSessionItem)

    @Update
    suspend fun updateShoppingSessionItem(item: ShoppingSessionItem)

    @Query("SELECT * FROM shopping_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ShoppingSession>>

    @Delete
    suspend fun deleteSession(session: ShoppingSession)

    @Query("DELETE FROM shopping_session_items WHERE sessionId = :sessionId")
    suspend fun deleteItemsForSession(sessionId: Long)

    @Query("SELECT * FROM shopping_session_items WHERE sessionId = :sessionId")
    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingSessionItem>>

    // Active Session (Persistence)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveSession(session: ActiveShoppingSession)

    @Query("SELECT * FROM active_shopping_session LIMIT 1")
    fun getActiveSession(): Flow<ActiveShoppingSession?>

    @Query("DELETE FROM active_shopping_session")
    suspend fun clearActiveSession()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActiveShoppingItem(item: ActiveShoppingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActiveShoppingItems(items: List<ActiveShoppingItem>)

    @Query("SELECT * FROM active_shopping_items")
    fun getActiveShoppingItems(): Flow<List<ActiveShoppingItem>>

    @Query("SELECT * FROM active_shopping_items")
    suspend fun getActiveShoppingItemsSnapshot(): List<ActiveShoppingItem>

    @Query("UPDATE active_shopping_items SET price = :price, quantity = :quantity WHERE itemId = :itemId")
    suspend fun updateActiveItemDetails(itemId: Long, price: Double, quantity: Double)

    @Query("DELETE FROM active_shopping_items")
    suspend fun clearActiveShoppingItems()

    @Transaction
    suspend fun completeShoppingSession(
        session: ShoppingSession,
        sessionItems: List<ShoppingSessionItem>,
        priceHistories: List<ItemPriceHistory>,
        itemsToMarkChecked: List<ShoppingItem>
    ) {
        val sessionId = insertShoppingSession(session)
        sessionItems.forEach { insertShoppingSessionItem(it.copy(sessionId = sessionId)) }
        priceHistories.forEach { insertPriceHistory(it) }
        itemsToMarkChecked.forEach { updateItem(it.copy(isChecked = true)) }
        clearActiveShoppingItems()
        clearActiveSession()
    }

    // Price History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: ItemPriceHistory)

    @Query("SELECT * FROM item_price_history WHERE itemName = :itemName ORDER BY timestamp DESC")
    fun getPriceHistoryForItem(itemName: String): Flow<List<ItemPriceHistory>>
}
