package com.mochisofts.mata.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.data.backup.BackupSettings
import com.mochisofts.mata.data.backup.DataMutationGate
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val mutationGate: DataMutationGate,
) : SettingsRepository {
    override val showCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_COMPLETED] ?: false
    }

    override val todoListMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[TODO_LIST_MODE] ?: "DATE"
    }

    override val uncategorizedEndHour: Flow<Int> = dataStore.data.map { preferences ->
        preferences[UNCATEGORIZED_END_HOUR] ?: 0
    }

    override val weekStart: Flow<DayOfWeek> = dataStore.data.map { preferences ->
        preferences[WEEK_START]
            ?.let { stored -> runCatching { DayOfWeek.valueOf(stored) }.getOrNull() }
            ?: DayOfWeek.MONDAY
    }

    override val theme: Flow<AppTheme> = dataStore.data.map { preferences ->
        AppTheme.fromStoredValue(preferences[THEME])
    }

    override val notificationPermissionRequested: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATION_PERMISSION_REQUESTED] ?: false
    }

    override val archiveSortOrder: Flow<ArchiveSortOrder> = dataStore.data.map { preferences ->
        ArchiveSortOrder.fromStoredValue(preferences[ARCHIVE_SORT_ORDER])
    }

    override suspend fun setShowCompleted(value: Boolean) {
        mutationGate.withMutation { dataStore.edit { it[SHOW_COMPLETED] = value } }
    }

    override suspend fun setTodoListMode(value: String) {
        mutationGate.withMutation { dataStore.edit { it[TODO_LIST_MODE] = value } }
    }

    override suspend fun setUncategorizedEndHour(value: Int) {
        require(value in 0..23)
        mutationGate.withMutation { dataStore.edit { it[UNCATEGORIZED_END_HOUR] = value } }
    }

    override suspend fun setWeekStart(value: DayOfWeek) {
        mutationGate.withMutation { dataStore.edit { it[WEEK_START] = value.name } }
    }

    override suspend fun setTheme(value: AppTheme) {
        mutationGate.withMutation { dataStore.edit { it[THEME] = value.code } }
    }

    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        mutationGate.withMutation { dataStore.edit { it[NOTIFICATION_PERMISSION_REQUESTED] = value } }
    }

    override suspend fun setArchiveSortOrder(value: ArchiveSortOrder) {
        mutationGate.withMutation { dataStore.edit { it[ARCHIVE_SORT_ORDER] = value.storedValue } }
    }

    internal suspend fun backupSnapshot(): BackupSettings {
        val preferences = dataStore.data.first()
        return BackupSettings(
            uncategorizedEndHour = preferences[UNCATEGORIZED_END_HOUR] ?: 0,
            weekStartDay = preferences[WEEK_START]
                ?.let { stored -> runCatching { DayOfWeek.valueOf(stored) }.getOrNull() }
                ?: DayOfWeek.MONDAY,
            showCompletedTodos = preferences[SHOW_COMPLETED] ?: false,
            theme = AppTheme.fromStoredValue(preferences[THEME]),
        )
    }

    internal suspend fun restoreBackupSettings(settings: BackupSettings) {
        dataStore.edit { preferences ->
            preferences[UNCATEGORIZED_END_HOUR] = settings.uncategorizedEndHour
            preferences[WEEK_START] = settings.weekStartDay.name
            preferences[SHOW_COMPLETED] = settings.showCompletedTodos
            preferences[THEME] = settings.theme.code
        }
    }

    private companion object {
        val SHOW_COMPLETED = booleanPreferencesKey("show_completed_todos")
        val TODO_LIST_MODE = stringPreferencesKey("todo_list_mode")
        val UNCATEGORIZED_END_HOUR = intPreferencesKey("uncategorized_end_hour")
        val WEEK_START = stringPreferencesKey("week_start")
        val THEME = stringPreferencesKey("theme")
        val NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("notification_permission_requested")
        val ARCHIVE_SORT_ORDER = stringPreferencesKey("archive_sort_order")
    }
}
