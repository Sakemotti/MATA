package com.mochisofts.mata.data.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mochisofts.mata.data.local.WidgetInstanceStateDao
import com.mochisofts.mata.data.local.WidgetInstanceStateEntity
import com.mochisofts.mata.domain.model.WidgetDisplayModel
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.widget.TodayTodoWidget
import com.mochisofts.mata.widget.TodayTodoWidgetReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun requestUpdate() {
        val request = OneTimeWorkRequest.Builder(WidgetRefreshWorker::class.java)
            .setInitialDelay(UPDATE_COALESCE_MILLIS, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        WidgetDiagnostics.log(context, "one-time update enqueued")
    }

    fun startPeriodic() {
        val request = PeriodicWorkRequest.Builder(
            WidgetRefreshWorker::class.java,
            1,
            TimeUnit.HOURS,
            15,
            TimeUnit.MINUTES,
        )
            .setInputData(workDataOf(INPUT_PERIODIC to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        WidgetDiagnostics.log(context, "periodic work enqueued")
    }

    fun stopPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        WidgetDiagnostics.log(context, "periodic work cancelled")
    }

    fun scheduleUndoExpiry(appWidgetId: Int, expiresAt: Long) {
        val delay = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequest.Builder(WidgetUndoExpiryWorker::class.java)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(INPUT_APP_WIDGET_ID to appWidgetId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            undoWorkName(appWidgetId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelUndoExpiry(appWidgetId: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(undoWorkName(appWidgetId))
    }

    fun ensureScheduledIfWidgetsExist() {
        if (activeAppWidgetIds(context).isNotEmpty()) {
            startPeriodic()
            requestUpdate()
        }
    }

    private fun undoWorkName(appWidgetId: Int) = "widget-undo-expiry-$appWidgetId"

    companion object {
        const val UPDATE_WORK_NAME = "widget-update"
        const val PERIODIC_WORK_NAME = "widget-periodic-reconcile"
        private const val UPDATE_COALESCE_MILLIS = 250L
    }
}

class WidgetRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dependencies = widgetEntryPoint(applicationContext)
        if (activeAppWidgetIds(applicationContext).isEmpty()) {
            dependencies.widgetUpdater().stopPeriodic()
        }
        val coordinator = dependencies.refreshCoordinator()
        if (coordinator.refreshAll()) return Result.success()
        if (runAttemptCount < MAX_RETRY_INDEX) return Result.retry()
        return if (inputData.getBoolean(INPUT_PERIODIC, false)) Result.success() else Result.failure()
    }

    private companion object {
        const val MAX_RETRY_INDEX = 3
    }
}

class WidgetUndoExpiryWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(INPUT_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()
        val dependencies = widgetEntryPoint(applicationContext)
        val dao = dependencies.widgetStateDao()
        val state = dao.find(appWidgetId) ?: return Result.success()
        val expiresAt = state.undoExpiresAt ?: return Result.success()
        val now = dependencies.clock().millis()
        if (expiresAt > now) {
            dependencies.widgetUpdater().scheduleUndoExpiry(appWidgetId, expiresAt)
            return Result.success()
        }
        dao.upsert(state.withoutUndo(now))
        runCatching {
            val glanceId = GlanceAppWidgetManager(applicationContext).getGlanceIdBy(appWidgetId)
            TodayTodoWidget().update(applicationContext, glanceId)
        }
        return Result.success()
    }
}

@Singleton
class WidgetRefreshCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayRepository: RoomWidgetDisplayRepository,
    private val stateDao: WidgetInstanceStateDao,
    private val alarmScheduler: WidgetRefreshAlarmScheduler,
    private val holidayRepository: HolidayRepository,
    private val clock: Clock,
) {
    private val glanceManager = GlanceAppWidgetManager(context)

    suspend fun refreshAll(preferredAppWidgetId: Int? = null): Boolean {
        val ids = glanceManager
            .getGlanceIds(TodayTodoWidget::class.java)
            .sortedBy {
                if (glanceManager.getAppWidgetId(it) == preferredAppWidgetId) 0 else 1
            }
        if (ids.isEmpty()) {
            alarmScheduler.cancel()
            stateDao.deleteAll()
            holidayRepository.pendingWidgetGeneration()?.let {
                holidayRepository.markWidgetGenerationProcessed(it)
            }
            return true
        }

        val now = clock.millis()
        val activeIds = ids.mapTo(mutableSetOf(), glanceManager::getAppWidgetId)
        stateDao.findAll()
            .filterNot { it.appWidgetId in activeIds }
            .forEach { stateDao.delete(it.appWidgetId) }
        val model = try {
            displayRepository.createSnapshot()
        } catch (_: Exception) {
            markFailure(ids, now, ERROR_DATA_LOAD)
            return false
        }
        val json = WidgetSnapshotJson.encode(model)
        var allSucceeded = true
        var nextRefreshAt = model.nextRefreshAt
        ids.forEach { id ->
            val appWidgetId = glanceManager.getAppWidgetId(id)
            val previous = stateDao.find(appWidgetId)
            val undoValid = previous?.undoExpiresAt?.let { it > now } == true
            val state = WidgetInstanceStateEntity(
                appWidgetId = appWidgetId,
                snapshotVersion = WidgetDisplayModel.CURRENT_VERSION,
                snapshotJson = json,
                lastSuccessAt = now,
                loadState = LOAD_READY,
                errorCode = null,
                lastFailureAt = null,
                undoOperationId = previous?.undoOperationId.takeIf { undoValid },
                undoTodoTitle = previous?.undoTodoTitle.takeIf { undoValid },
                undoExpiresAt = previous?.undoExpiresAt.takeIf { undoValid },
                nextRefreshAt = null,
                updatedAt = now,
            )
            state.undoExpiresAt?.let { nextRefreshAt = minOf(nextRefreshAt, it) }
            stateDao.upsert(state)
            if (runCatching { TodayTodoWidget().update(context, id) }.isFailure) {
                allSucceeded = false
                stateDao.upsert(
                    state.copy(
                        loadState = LOAD_STALE,
                        errorCode = ERROR_GLANCE_UPDATE,
                        lastFailureAt = now,
                        updatedAt = now,
                    ),
                )
            }
            WidgetDiagnostics.logState(context, stateDao.find(appWidgetId) ?: state)
        }
        stateDao.findAll().filter { state -> state.appWidgetId in activeIds }
            .forEach { state -> stateDao.upsert(state.copy(nextRefreshAt = nextRefreshAt)) }
        alarmScheduler.schedule(nextRefreshAt)
        if (allSucceeded) {
            holidayRepository.pendingWidgetGeneration()?.let {
                holidayRepository.markWidgetGenerationProcessed(it)
            }
        }
        return allSucceeded
    }

    private suspend fun markFailure(ids: List<GlanceId>, now: Long, errorCode: String) {
        ids.forEach { id ->
            val appWidgetId = glanceManager.getAppWidgetId(id)
            val previous = stateDao.find(appWidgetId)
            stateDao.upsert(
                previous?.copy(
                    loadState = if (previous.snapshotJson == null) LOAD_ERROR else LOAD_STALE,
                    errorCode = errorCode,
                    lastFailureAt = now,
                    updatedAt = now,
                ) ?: WidgetInstanceStateEntity(
                    appWidgetId = appWidgetId,
                    snapshotVersion = WidgetDisplayModel.CURRENT_VERSION,
                    snapshotJson = null,
                    lastSuccessAt = null,
                    loadState = LOAD_ERROR,
                    errorCode = errorCode,
                    lastFailureAt = now,
                    undoOperationId = null,
                    undoTodoTitle = null,
                    undoExpiresAt = null,
                    nextRefreshAt = null,
                    updatedAt = now,
                ),
            )
            runCatching { TodayTodoWidget().update(context, id) }
        }
    }
}

@Singleton
class WidgetRefreshAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(triggerAt: Long) {
        if (activeAppWidgetIds(context).isEmpty()) {
            cancel()
            return
        }
        val safeTrigger = triggerAt.coerceAtLeast(clock.millis() + MIN_ALARM_DELAY_MILLIS)
        alarmManager.set(AlarmManager.RTC, safeTrigger, pendingIntent())
        WidgetDiagnostics.log(context, "next alarm=$safeTrigger")
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
        WidgetDiagnostics.log(context, "alarm cancelled")
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_WIDGET_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ALARM_REQUEST_CODE = 20_026
        const val MIN_ALARM_DELAY_MILLIS = 1_000L
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = widgetEntryPoint(context).widgetUpdater()
        when (intent.action) {
            ACTION_WIDGET_REFRESH -> updater.requestUpdate()
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED,
            -> updater.ensureScheduledIfWidgetsExist()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun refreshCoordinator(): WidgetRefreshCoordinator
    fun widgetStateDao(): WidgetInstanceStateDao
    fun widgetUpdater(): WidgetUpdater
    fun clock(): Clock
}

internal fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

internal fun WidgetInstanceStateEntity.withoutUndo(now: Long) = copy(
    undoOperationId = null,
    undoTodoTitle = null,
    undoExpiresAt = null,
    updatedAt = now,
)

internal fun activeAppWidgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
    .getAppWidgetIds(ComponentName(context, TodayTodoWidgetReceiver::class.java))

const val LOAD_LOADING = "loading"
const val LOAD_READY = "ready"
const val LOAD_STALE = "stale"
const val LOAD_ERROR = "error"
const val LOAD_ACTION_ERROR = "action_error"
const val ERROR_DATA_LOAD = "widget_data_load"
const val ERROR_GLANCE_UPDATE = "widget_glance_update"
const val ERROR_COMPLETE = "widget_complete"
const val ACTION_WIDGET_REFRESH = "com.mochisofts.mata.action.WIDGET_REFRESH"
private const val INPUT_PERIODIC = "periodic"
private const val INPUT_APP_WIDGET_ID = "app_widget_id"

private object WidgetDiagnostics {
    private const val TAG = "MataWidget"

    fun log(context: Context, message: String) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        Log.d(TAG, message)
    }

    fun logState(context: Context, state: WidgetInstanceStateEntity) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(state.appWidgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val layout = when {
            width < 180 -> "compact"
            width >= 300 && height < 160 -> "wide"
            else -> "standard"
        }
        Log.d(
            TAG,
            "id=${state.appWidgetId} size=${width}x$height layout=$layout " +
                "snapshot=${state.snapshotVersion} lastSuccess=${state.lastSuccessAt} " +
                "state=${state.loadState} error=${state.errorCode} " +
                "next=${state.nextRefreshAt} undoUntil=${state.undoExpiresAt}",
        )
    }
}
