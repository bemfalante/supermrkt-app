package com.example.supermarketlist.util

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    // Currently no settings are managed here as Gemini API Key was removed.
}
