package com.akillikumanda.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ayarlar")

data class AppSettings(
    val haptics: Boolean = true,
    /** Kumanda Bul'da her aday için en fazla test gönderimi. */
    val maxTestsPerCandidate: Int = 3,
)

class SettingsRepository(private val context: Context) {
    private val hapticsKey = booleanPreferencesKey("haptics")
    private val maxTestsKey = intPreferencesKey("max_tests_per_candidate")

    val settings: Flow<AppSettings> = context.dataStore.data.map {
        AppSettings(
            haptics = it[hapticsKey] ?: true,
            maxTestsPerCandidate = (it[maxTestsKey] ?: 3).coerceIn(1, 5),
        )
    }

    suspend fun setHaptics(enabled: Boolean) { context.dataStore.edit { it[hapticsKey] = enabled } }
    suspend fun setMaxTests(value: Int) { context.dataStore.edit { it[maxTestsKey] = value.coerceIn(1, 5) } }
}
