package com.mochisofts.mata.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochisofts.mata.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val showCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_COMPLETED] ?: false
    }

    override val todoListMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[TODO_LIST_MODE] ?: "DATE"
    }

    override suspend fun setShowCompleted(value: Boolean) {
        dataStore.edit { it[SHOW_COMPLETED] = value }
    }

    override suspend fun setTodoListMode(value: String) {
        dataStore.edit { it[TODO_LIST_MODE] = value }
    }

    private companion object {
        val SHOW_COMPLETED = booleanPreferencesKey("show_completed_todos")
        val TODO_LIST_MODE = stringPreferencesKey("todo_list_mode")
    }
}
