package com.example.supermarketlist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    val isChecked: Boolean = false
)

@Entity(tableName = "shopping_sessions")
data class ShoppingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "shopping_session_items")
data class ShoppingSessionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val itemName: String,
    val price: Double,
    val quantity: Double = 1.0,
    val status: String // "BOUGHT", "FOUND_NOT_BOUGHT", "NOT_FOUND"
)

@Entity(tableName = "item_price_history")
data class ItemPriceHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemName: String,
    val price: Double,
    val quantity: Double = 1.0,
    val categoryName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String
)

@Entity(tableName = "active_shopping_session")
data class ActiveShoppingSession(
    @PrimaryKey val id: Int = 1,
    val categoryId: Long?
)
