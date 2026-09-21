package com.feiqi.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "feiqi_preferences")

class PreferencesDataStore(private val context: Context) {

    private object Keys {
        val MONTHLY_BUDGET = doublePreferencesKey("monthly_budget")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val HABIT_REMINDER_ENABLED = booleanPreferencesKey("habit_reminder_enabled")
        val WEIGHT_RECORDS = stringPreferencesKey("weight_records")
        val USER_PROFILE = stringPreferencesKey("user_profile")
        val SHOPPING_ITEMS = stringPreferencesKey("shopping_items")
    }

    val monthlyBudget: Flow<Double> = context.dataStore.data.map { prefs ->
        prefs[Keys.MONTHLY_BUDGET] ?: 5000.0
    }

    suspend fun setMonthlyBudget(value: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MONTHLY_BUDGET] = value.coerceAtLeast(0.0)
        }
    }

    val notificationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.NOTIFICATION_ENABLED] ?: true
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_ENABLED] = enabled
        }
    }

    val habitReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HABIT_REMINDER_ENABLED] ?: true
    }

    suspend fun setHabitReminderEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HABIT_REMINDER_ENABLED] = enabled
        }
    }

    val weightRecordsJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.WEIGHT_RECORDS] ?: "[]"
    }

    suspend fun setWeightRecordsJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_RECORDS] = json
        }
    }

    val userProfileJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.USER_PROFILE] ?: ""
    }

    suspend fun setUserProfileJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_PROFILE] = json
        }
    }

    val shoppingItemsJson: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOPPING_ITEMS] ?: "[]"
    }

    suspend fun setShoppingItemsJson(json: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOPPING_ITEMS] = json
        }
    }
}
