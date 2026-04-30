package com.example.supermarketlist.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY]
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = key
        }
    }
}
