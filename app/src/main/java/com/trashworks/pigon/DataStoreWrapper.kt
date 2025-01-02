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
    suspend fun saveString(value: String, key: String? = null) {
        if (key == null) {
            context.dataStore.edit { preferences ->
                preferences[STRING_KEY] = value
            }
        } else {
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey(key)] = value
            }
        }

    }

    // Retrieve a string from DataStore
    suspend fun getString(key: String? = null): String? {
        if (key == null) {
            return context.dataStore.data
                .map { preferences -> preferences[STRING_KEY] }
                .first()
        } else {
            return context.dataStore.data
                .map { preferences -> preferences[stringPreferencesKey(key)] }
                .first()
        }

    }

    // Check if the string exists in DataStore
    suspend fun hasString(key: String? = null): Boolean {
        if (getString(key) == null) {
            return  false
        }

        if (getString(key) == "") {
            return  false
        }

        return  true

    }
}
