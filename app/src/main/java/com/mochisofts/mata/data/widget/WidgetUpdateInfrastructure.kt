package com.mochisofts.mata.data.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
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
import com.mochisofts.mata.core.common.FailureCategory
import com.mochisofts.mata.core.observability.DiagnosticEvent
import com.mochisofts.mata.core.observability.DiagnosticEventCode
import com.mochisofts.mata.core.observability.DiagnosticLevel
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.core.observability.DiagnosticResult
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnostics: DiagnosticLogger,
) {
    fun requestUpdate() = enqueueUpdate(
        workName = UPDATE_WORK_NAME,
        initialDelayMillis = UPDATE_COALESCE_MILLIS,
    )

    fun requestImmediateUpdate() = enqueueUpdate(
        workName = IMMEDIATE_UPDATE_WORK_NAME,
        initialDelayMillis = 0,
    )

    private fun enqueueUpdate(workName: String, initialDelayMillis: Long) {
        val request = OneTimeWorkRequest.Builder(WidgetRefreshWorker::class.java)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        diagnostics.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_UPDATE_ENQUEUED,
                level = DiagnosticLevel.DEBUG,
                result = DiagnosticResult.SUCCESS,
            ),
        )
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
        diagnostics.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_PERIODIC_SCHEDULED,
                level = DiagnosticLevel.INFO,
                result = DiagnosticResult.SUCCESS,
            ),
        )
    }

    fun stopPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        diagnostics.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_PERIODIC_CANCELLED,
                level = DiagnosticLevel.INFO,
                result = DiagnosticResult.CANCELLED,
            ),
        )
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
        const val IMMEDIATE_UPDATE_WORK_NAME = "widget-update-immediate"
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
    private val diagnostics: DiagnosticLogger,
) {
    private val glanceManager = GlanceAppWidgetManager(context)
    private val serialExecutor = WidgetRefreshSerialExecutor()

    suspend fun refreshAll(preferredAppWidgetId: Int? = null): Boolean = serialExecutor.run {
        refreshAllSerially(preferredAppWidgetId)
    }

    private suspend fun refreshAllSerially(preferredAppWidgetId: Int?): Boolean {
        val operationId = diagnostics.newOperationId()
        val startedAt = SystemClock.elapsedRealtime()
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
            reportRefresh(
                result = DiagnosticResult.SUCCESS,
                count = 0,
                startedAt = startedAt,
                operationId = operationId,
            )
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
            reportRefresh(
                result = DiagnosticResult.FAILURE,
                count = ids.size,
                startedAt = startedAt,
                operationId = operationId,
                failureCategory = FailureCategory.TEMPORARY_LOCAL,
            )
            return false
        }
        val json = WidgetSnapshotJson.encode(model)
        var allSucceeded = true
        var nextRefreshAt = model.nextRefreshAt
        ids.forEach { id ->
            val appWidgetId = glanceManager.getAppWidgetId(id)
            val state = WidgetInstanceStateEntity(
                appWidgetId = appWidgetId,
                snapshotVersion = WidgetDisplayModel.CURRENT_VERSION,
                snapshotJson = json,
                lastSuccessAt = now,
                loadState = LOAD_READY,
                errorCode = null,
                lastFailureAt = null,
                undoOperationId = null,
                undoTodoTitle = null,
                undoExpiresAt = null,
                nextRefreshAt = null,
                updatedAt = now,
            )
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
        }
        stateDao.findAll().filter { state -> state.appWidgetId in activeIds }
            .forEach { state -> stateDao.upsert(state.copy(nextRefreshAt = nextRefreshAt)) }
        alarmScheduler.schedule(nextRefreshAt)
        if (allSucceeded) {
            holidayRepository.pendingWidgetGeneration()?.let {
                holidayRepository.markWidgetGenerationProcessed(it)
            }
        }
        reportRefresh(
            result = if (allSucceeded) DiagnosticResult.SUCCESS else DiagnosticResult.FAILURE,
            count = ids.size,
            startedAt = startedAt,
            operationId = operationId,
            failureCategory = FailureCategory.TEMPORARY_LOCAL.takeUnless { allSucceeded },
        )
        return allSucceeded
    }

    private fun reportRefresh(
        result: DiagnosticResult,
        count: Int,
        startedAt: Long,
        operationId: String,
        failureCategory: FailureCategory? = null,
    ) {
        diagnostics.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_REFRESH_FINISHED,
                level = if (result == DiagnosticResult.SUCCESS) {
                    DiagnosticLevel.INFO
                } else {
                    DiagnosticLevel.WARN
                },
                result = result,
                failureCategory = failureCategory,
                count = count,
                durationMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0),
                operationId = operationId,
            ),
        )
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

internal class WidgetRefreshSerialExecutor {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

@Singleton
class WidgetRefreshAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val diagnostics: DiagnosticLogger,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(triggerAt: Long) {
        if (activeAppWidgetIds(context).isEmpty()) {
            cancel()
            return
        }
        val safeTrigger = triggerAt.coerceAtLeast(clock.millis() + MIN_ALARM_DELAY_MILLIS)
        alarmManager.set(AlarmManager.RTC, safeTrigger, pendingIntent())
        diagnostics.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_ALARM_SCHEDULED,
                level = DiagnosticLevel.DEBUG,
                result = DiagnosticResult.SUCCESS,
            ),
        )
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
        diagnostics.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_ALARM_CANCELLED,
                level = DiagnosticLevel.DEBUG,
                result = DiagnosticResult.CANCELLED,
            ),
        )
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
