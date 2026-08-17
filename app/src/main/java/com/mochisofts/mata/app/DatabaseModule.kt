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
import com.mochisofts.mata.data.local.MIGRATION_3_4
import com.mochisofts.mata.data.local.MIGRATION_4_5
import com.mochisofts.mata.data.local.MIGRATION_5_6
import com.mochisofts.mata.data.local.HolidayDao
import com.mochisofts.mata.data.local.HolidayFetchStateDao
import com.mochisofts.mata.data.local.HolidayUpdateStateDao
import com.mochisofts.mata.data.local.WidgetInstanceStateDao
import com.mochisofts.mata.data.local.PeriodResultDao
import com.mochisofts.mata.data.local.ScheduledNotificationDao
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoRuntimeStateDao
import com.mochisofts.mata.data.repository.RoomCategoryRepository
import com.mochisofts.mata.data.repository.RoomTodoRepository
import com.mochisofts.mata.data.repository.RoomHistoryReconciler
import com.mochisofts.mata.data.repository.RoomHistoryRepository
import com.mochisofts.mata.data.repository.RoomArchiveRepository
import com.mochisofts.mata.data.repository.DataStoreSettingsRepository
import com.mochisofts.mata.data.repository.RoomHolidayRepository
import com.mochisofts.mata.data.holiday.HolidayHttpClient
import com.mochisofts.mata.data.holiday.UrlConnectionHolidayHttpClient
import com.mochisofts.mata.core.notification.AlarmGateway
import com.mochisofts.mata.data.notification.AndroidAlarmGateway
import com.mochisofts.mata.data.notification.AndroidNotificationScheduler
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HistoryRepository
import com.mochisofts.mata.domain.repository.ArchiveRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
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
    abstract fun bindArchiveRepository(repository: RoomArchiveRepository): ArchiveRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(repository: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTodoRepository(repository: RoomTodoRepository): TodoRepository

    @Binds
    @Singleton
    abstract fun bindHistoryReconciler(reconciler: RoomHistoryReconciler): HistoryReconciler

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(repository: RoomHistoryRepository): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindNotificationScheduler(scheduler: AndroidNotificationScheduler): NotificationScheduler

    @Binds
    @Singleton
    abstract fun bindAlarmGateway(gateway: AndroidAlarmGateway): AlarmGateway

    @Binds
    @Singleton
    abstract fun bindHolidayRepository(repository: RoomHolidayRepository): HolidayRepository

    @Binds
    @Singleton
    abstract fun bindHolidayHttpClient(client: UrlConnectionHolidayHttpClient): HolidayHttpClient
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MataDatabase =
        Room.databaseBuilder(context, MataDatabase::class.java, "mata.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
            .build()

    @Provides
    fun provideCategoryDao(database: MataDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideTodoDao(database: MataDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideTodoExecutionDao(database: MataDatabase): TodoExecutionDao = database.todoExecutionDao()

    @Provides
    fun providePeriodResultDao(database: MataDatabase): PeriodResultDao = database.periodResultDao()

    @Provides
    fun provideTodoRuntimeStateDao(database: MataDatabase): TodoRuntimeStateDao =
        database.todoRuntimeStateDao()

    @Provides
    fun provideTodoNotificationDao(database: MataDatabase): TodoNotificationDao =
        database.todoNotificationDao()

    @Provides
    fun provideScheduledNotificationDao(database: MataDatabase): ScheduledNotificationDao =
        database.scheduledNotificationDao()

    @Provides
    fun provideHolidayDao(database: MataDatabase): HolidayDao = database.holidayDao()

    @Provides
    fun provideHolidayFetchStateDao(database: MataDatabase): HolidayFetchStateDao =
        database.holidayFetchStateDao()

    @Provides
    fun provideHolidayUpdateStateDao(database: MataDatabase): HolidayUpdateStateDao =
        database.holidayUpdateStateDao()

    @Provides
    fun provideWidgetInstanceStateDao(database: MataDatabase): WidgetInstanceStateDao =
        database.widgetInstanceStateDao()

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
