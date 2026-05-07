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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem): Long

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)

    // Item-Category Relationship
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemCategoryCrossRef(crossRef: ItemCategoryCrossRef)

    @Query("DELETE FROM item_category_cross_ref WHERE itemId = :itemId")
    suspend fun deleteItemCategoryCrossRefs(itemId: Long)

    @Query("""
        SELECT categories.* FROM categories
        INNER JOIN item_category_cross_ref ON categories.id = item_category_cross_ref.categoryId
        WHERE item_category_cross_ref.itemId = :itemId
    """)
    fun getCategoriesForItem(itemId: Long): Flow<List<Category>>

    @Query("""
        SELECT shopping_items.* FROM shopping_items
        INNER JOIN item_category_cross_ref ON shopping_items.id = item_category_cross_ref.itemId
        WHERE item_category_cross_ref.categoryId = :categoryId
    """)
    fun getItemsByCategory(categoryId: Long): Flow<List<ShoppingItem>>

    @Query("""
        SELECT * FROM shopping_items
        WHERE id NOT IN (SELECT itemId FROM item_category_cross_ref)
    """)
    fun getUncategorizedItems(): Flow<List<ShoppingItem>>

    // Shopping Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSession(session: ShoppingSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingSessionItem(item: ShoppingSessionItem)

    @Query("SELECT * FROM shopping_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ShoppingSession>>

    @Query("SELECT * FROM shopping_session_items WHERE sessionId = :sessionId")
    fun getItemsForSession(sessionId: Long): Flow<List<ShoppingSessionItem>>

    // Active Session (Persistence)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveSession(session: ActiveShoppingSession)

    @Query("SELECT * FROM active_shopping_session LIMIT 1")
    fun getActiveSession(): Flow<ActiveShoppingSession?>

    @Query("DELETE FROM active_shopping_session")
    suspend fun clearActiveSession()

    // Price History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: ItemPriceHistory)

    @Query("SELECT * FROM item_price_history WHERE itemName = :itemName ORDER BY timestamp DESC")
    fun getPriceHistoryForItem(itemName: String): Flow<List<ItemPriceHistory>>
}
