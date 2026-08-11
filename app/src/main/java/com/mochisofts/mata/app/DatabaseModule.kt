package com.mochisofts.mata.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.MIGRATION_1_2
import com.mochisofts.mata.data.local.MIGRATION_2_3
import com.mochisofts.mata.data.local.ScheduledNotificationDao
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.repository.RoomCategoryRepository
import com.mochisofts.mata.data.repository.RoomTodoRepository
import com.mochisofts.mata.data.repository.DataStoreSettingsRepository
import com.mochisofts.mata.core.notification.AlarmGateway
import com.mochisofts.mata.data.notification.AndroidAlarmGateway
import com.mochisofts.mata.data.notification.AndroidNotificationScheduler
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindCategoryRepository(repository: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTodoRepository(repository: RoomTodoRepository): TodoRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindNotificationScheduler(scheduler: AndroidNotificationScheduler): NotificationScheduler

    @Binds
    @Singleton
    abstract fun bindAlarmGateway(gateway: AndroidAlarmGateway): AlarmGateway
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MataDatabase =
        Room.databaseBuilder(context, MataDatabase::class.java, "mata.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideCategoryDao(database: MataDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTodoDao(database: MataDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideTodoExecutionDao(database: MataDatabase): TodoExecutionDao = database.todoExecutionDao()

    @Provides
    fun provideTodoNotificationDao(database: MataDatabase): TodoNotificationDao =
        database.todoNotificationDao()

    @Provides
    fun provideScheduledNotificationDao(database: MataDatabase): ScheduledNotificationDao =
        database.scheduledNotificationDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("settings.preferences_pb")
        }
}
