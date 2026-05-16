package com.example.supermarketlist.data.local.dao

import androidx.room.*
import com.example.supermarketlist.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {
    // Categories
    @Query("SELECT * FROM categories ORDER BY displayOrder ASC, name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    // Items
    @Transaction
    @Query("SELECT * FROM shopping_items ORDER BY name ASC")
    fun getAllItemsWithCategories(): Flow<List<ShoppingItemWithCategories>>

    @Transaction
    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItemsWithCategoriesSnapshot(): List<ShoppingItemWithCategories>

    @Query("SELECT * FROM shopping_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItemsSnapshot(): List<ShoppingItem>

    @Query("""
        SELECT shopping_items.* FROM shopping_items
        INNER JOIN item_category_cross_ref ON shopping_items.id = item_category_cross_ref.itemId
        WHERE item_category_cross_ref.categoryId = :categoryId
        ORDER BY shopping_items.isChecked ASC, shopping_items.name ASC
    """)
    fun getItemsByCategory(categoryId: Long): Flow<List<ShoppingItem>>

    @Query("""
        SELECT * FROM shopping_items
        WHERE id NOT IN (SELECT itemId FROM item_category_cross_ref)
        ORDER BY isChecked ASC, name ASC
    """)
    fun getUncategorizedItems(): Flow<List<ShoppingItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem): Long

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)

    // CrossRef
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemCategoryCrossRef(crossRef: ItemCategoryCrossRef)

    @Query("DELETE FROM item_category_cross_ref WHERE itemId = :itemId")
    suspend fun deleteItemCategoryCrossRefs(itemId: Long)

    @Query("SELECT categoryId FROM item_category_cross_ref WHERE itemId = :itemId")
    suspend fun getCategoryIdsForItem(itemId: Long): List<Long>

    // Shopping Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSession(session: ShoppingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSessionItem(item: ShoppingSessionItem)

    @Update
    suspend fun updateShoppingSessionItem(item: ShoppingSessionItem)

    @Query("SELECT * FROM shopping_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ShoppingSession>>

    @Query("UPDATE shopping_sessions SET paymentMethod = :paymentMethod WHERE id = :sessionId")
    suspend fun updateShoppingSessionPayment(sessionId: Long, paymentMethod: String)

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
