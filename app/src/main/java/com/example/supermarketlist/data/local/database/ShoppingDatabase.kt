package com.example.supermarketlist.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.supermarketlist.data.local.dao.ShoppingDao
import com.example.supermarketlist.data.local.entity.*

@Database(
    entities = [
        Category::class,
        ShoppingItem::class,
        ItemCategoryCrossRef::class,
        ShoppingSession::class,
        ShoppingSessionItem::class,
        ItemPriceHistory::class,
        ActiveShoppingSession::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getDatabase(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
