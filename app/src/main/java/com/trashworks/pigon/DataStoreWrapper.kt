package com.trashworks.pigon

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Extension property for DataStore
private val Context.dataStore by preferencesDataStore("app_preferences")

class DataStoreWrapper(private val context: Context) {

    private val STRING_KEY = stringPreferencesKey("stored_string")

    // Save a string to DataStore
    suspend fun saveString(value: String) {
        context.dataStore.edit { preferences ->
            preferences[STRING_KEY] = value
        }
    }

    // Retrieve a string from DataStore
    suspend fun getString(): String? {
        return context.dataStore.data
            .map { preferences -> preferences[STRING_KEY] }
            .first()
    }

    // Check if the string exists in DataStore
    suspend fun hasString(): Boolean {
        return getString() != null
    }
}
