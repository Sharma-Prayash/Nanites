package com.team.riskauditor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auditor_prefs")

class AppPrefs(private val context: Context) {

    val includeSystemApps: Flow<Boolean> = context.dataStore.data.map { it[INCLUDE_SYSTEM] ?: false }

    suspend fun setIncludeSystemApps(value: Boolean) {
        context.dataStore.edit { it[INCLUDE_SYSTEM] = value }
    }

    private companion object {
        val INCLUDE_SYSTEM = booleanPreferencesKey("include_system_apps")
    }
}
