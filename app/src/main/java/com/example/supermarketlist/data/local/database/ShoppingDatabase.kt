package com.example.supermarketlist.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.supermarketlist.data.local.dao.ShoppingDao
import com.example.supermarketlist.data.local.entity.Category
import com.example.supermarketlist.data.local.entity.ShoppingItem

@Database(entities = [Category::class, ShoppingItem::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
