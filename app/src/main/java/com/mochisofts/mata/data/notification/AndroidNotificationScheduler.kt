package com.mochisofts.mata.data.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mochisofts.mata.app.notification.NotificationAlarmReceiver
import com.mochisofts.mata.app.notification.NotificationPresenter
import com.mochisofts.mata.app.notification.NotificationReconcileReceiver
import com.mochisofts.mata.core.notification.AlarmGateway
import com.mochisofts.mata.data.local.ScheduledNotificationDao
import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.NotificationValidationError
import com.mochisofts.mata.domain.model.nextNotificationCandidate
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.validateNotifications
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AndroidNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoDao: TodoDao,
    private val executionDao: TodoExecutionDao,
    private val notificationDao: TodoNotificationDao,
    private val scheduledDao: ScheduledNotificationDao,
    private val settingsRepository: SettingsRepository,
    private val holidayRepository: HolidayRepository,
    private val alarmGateway: AlarmGateway,
    private val presenter: NotificationPresenter,
    private val systemStateProvider: NotificationSystemStateProvider,
    private val clock: Clock,
) : NotificationScheduler {
    private val mutex = Mutex()

    override val notificationCount: Flow<Int> = notificationDao.observeCount()

    override fun systemState(): NotificationSystemState = systemStateProvider.current()

    private suspend fun reconcileTodoLocked(
        todoId: String,
        holidays: Set<LocalDate>,
        mode: AlarmReconciliationMode,
    ) {
        val entity = todoDao.findById(todoId)
        val notificationEntities = notificationDao.findForTodo(todoId)
        if (entity == null || entity.archivedAt != null || notificationEntities.isEmpty()) {
            cancelTodoLocked(todoId)
            return
        }

        val todo = entity.toDomain()
        val endHour = settingsRepository.dayEndHour.first()
        val weekStart = settingsRepository.weekStart.first()
        val executions = executionDao.findForTodo(todoId)
        val completedDates = executions.filter { TodoState.fromStoredValue(it.status) == TodoState.COMPLETED }
            .mapTo(mutableSetOf()) { LocalDate.parse(it.logicalDate) }
        val actedDates = executions.mapTo(mutableSetOf()) { LocalDate.parse(it.logicalDate) }
        presenter.dismissReminders(todoId, actedDates)
        val now = ZonedDateTime.now(clock)
        val invalidDate = maxOf(todo.startDate, logicalDate(now, endHour))
        val plans = notificationEntities.mapNotNull { setting ->
            val notification = setting.toDomain()
            val errors = validateNotifications(listOf(notification), todo.dueMinutes, endHour)
            if (errors.isNotEmpty()) {
                NotificationPlan.Invalid(
                    notification = notification,
                    logicalDate = invalidDate,
                    failureCode = validationFailureCode(errors),
                )
            } else {
                nextNotificationCandidate(
                    todo = todo,
                    notification = notification,
                    endHour = endHour,
                    now = now,
                    weekStart = weekStart,
                    completedDates = completedDates,
                    actedDates = actedDates,
                    holidays = holidays,
                )?.let(NotificationPlan::Candidate)
            }
        }
        val desiredKeys = plans.mapTo(mutableSetOf()) { plan ->
            candidateKey(todo, plan.notification, plan.logicalDate)
        }
        val existing = scheduledDao.findForTodo(todoId)
        existing.filterNot { it.candidateKey in desiredKeys }.forEach { stale ->
            alarmGateway.cancel(stale.candidateKey, stale.requestCode)
            scheduledDao.delete(stale.candidateKey)
        }

        val state = systemState()
        var nextRequestCode = scheduledDao.maxRequestCode() + 1
        plans.forEach { plan ->
            val key = candidateKey(todo, plan.notification, plan.logicalDate)
            val previous = existing.firstOrNull { it.candidateKey == key }
            val requestCode = previous?.requestCode ?: nextRequestCode++
            val createdAt = previous?.createdAt ?: clock.millis()
            val desiredMode = if (state.canScheduleExactAlarms) MODE_EXACT else MODE_INEXACT
            if (plan is NotificationPlan.Invalid) {
                previous
                    ?.takeIf { it.state == STATE_PENDING || it.state == STATE_SCHEDULED }
                    ?.let { alarmGateway.cancel(it.candidateKey, it.requestCode) }
                scheduledDao.upsert(
                    ScheduledNotificationEntity(
                        candidateKey = key,
                        todoId = todoId,
                        notificationSettingId = plan.notification.id,
                        logicalDate = plan.logicalDate.toString(),
                        definitionRevision = todo.definitionRevision,
                        triggerAt = 0,
                        requestCode = requestCode,
                        schedulingMode = desiredMode,
                        state = STATE_SUPPRESSED,
                        failureCode = plan.failureCode,
                        createdAt = createdAt,
                        updatedAt = clock.millis(),
                    ),
                )
                return@forEach
            }

            val candidate = (plan as NotificationPlan.Candidate).value
            if (canReuseScheduledAlarm(
                    previous = previous,
                    desiredTriggerAt = candidate.triggerAt.toInstant().toEpochMilli(),
                    desiredMode = desiredMode,
                    canPostNotifications = state.canPostNotifications,
                    reconciliationMode = mode,
                )
            ) {
                return@forEach
            }
            previous?.let { alarmGateway.cancel(it.candidateKey, it.requestCode) }
            var record = ScheduledNotificationEntity(
                candidateKey = key,
                todoId = todoId,
                notificationSettingId = candidate.notification.id,
                logicalDate = candidate.logicalDate.toString(),
                definitionRevision = todo.definitionRevision,
                triggerAt = candidate.triggerAt.toInstant().toEpochMilli(),
                requestCode = requestCode,
                schedulingMode = desiredMode,
                state = if (state.canPostNotifications) STATE_PENDING else STATE_SUPPRESSED,
                failureCode = if (state.canPostNotifications) null else FAILURE_PERMISSION,
                createdAt = createdAt,
                updatedAt = clock.millis(),
            )
            scheduledDao.upsert(record)
            if (!state.canPostNotifications) return@forEach

            record = try {
                alarmGateway.schedule(key, requestCode, record.triggerAt, state.canScheduleExactAlarms)
                record.copy(state = STATE_SCHEDULED, failureCode = null, updatedAt = clock.millis())
            } catch (_: SecurityException) {
                try {
                    alarmGateway.schedule(key, requestCode, record.triggerAt, exact = false)
                    record.copy(
                        schedulingMode = MODE_INEXACT,
                        state = STATE_SCHEDULED,
                        failureCode = null,
                        updatedAt = clock.millis(),
                    )
                } catch (_: RuntimeException) {
                    record.copy(state = STATE_FAILED, failureCode = FAILURE_ALARM, updatedAt = clock.millis())
                }
            } catch (_: RuntimeException) {
                record.copy(state = STATE_FAILED, failureCode = FAILURE_ALARM, updatedAt = clock.millis())
            }
            scheduledDao.upsert(record)
        }
    }

    override suspend fun reconcileTodo(todoId: String) {
        mutex.withLock {
            reconcileTodoLocked(
                todoId = todoId,
                holidays = holidayRepository.currentSnapshot().dates,
                mode = AlarmReconciliationMode.INCREMENTAL,
            )
            updateReconcileReceiver()
        }
    }

    override suspend fun reconcileAll() = reconcileAll(AlarmReconciliationMode.INCREMENTAL)

    override suspend fun rebuildAll() = reconcileAll(AlarmReconciliationMode.REBUILD_OS_REGISTRATIONS)

    private suspend fun reconcileAll(mode: AlarmReconciliationMode) {
        mutex.withLock {
            presenter.dismissLegacyNotifications()
            val holidays = holidayRepository.currentSnapshot().dates
            val activeTodos = todoDao.findActiveWithNotifications()
            val activeIds = activeTodos.mapTo(mutableSetOf(), TodoEntity::id)
            scheduledDao.findTodoIds().filterNot(activeIds::contains).forEach { todoId ->
                cancelTodoLocked(todoId)
            }
            activeTodos.forEach { reconcileTodoLocked(it.id, holidays, mode) }
            updateReconcileReceiver()
        }
    }

    override suspend fun cancelTodo(todoId: String) {
        mutex.withLock {
            cancelTodoLocked(todoId)
            updateReconcileReceiver()
        }
    }

    private suspend fun cancelTodoLocked(todoId: String) {
        scheduledDao.findForTodo(todoId).forEach { scheduled ->
            alarmGateway.cancel(scheduled.candidateKey, scheduled.requestCode)
        }
        scheduledDao.deleteForTodo(todoId)
        presenter.dismissTodo(todoId)
    }

    private suspend fun updateReconcileReceiver() {
        val enabled = notificationDao.count() > 0
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, NotificationReconcileReceiver::class.java),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun candidateKey(todo: Todo, notification: TodoNotification, logicalDate: LocalDate): String =
        "${todo.id}|${notification.id}|$logicalDate|${todo.definitionRevision}"

    private fun TodoEntity.toDomain() = Todo(
        id = id,
        title = title,
        description = description,
        categoryId = categoryId,
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let(LocalDate::parse),
        recurrenceRule = RecurrenceRuleJson.decode(
            typeCode = recurrenceType,
            paramsVersion = repeatParamsVersion,
            paramsJson = repeatParamsJson,
        ),
        dueMinutes = dueMinutes,
        definitionRevision = definitionRevision,
        archivedAt = archivedAt,
        createdAt = createdAt,
    )

    private fun TodoNotificationEntity.toDomain() = TodoNotification(
        id = id,
        relation = NotificationRelation.fromStoredValue(relation),
        amount = amount,
        unit = NotificationUnit.fromStoredValue(unit),
    )

    private fun validationFailureCode(errors: Set<NotificationValidationError>): String =
        "$FAILURE_INVALID_PREFIX${errors.minBy { it.ordinal }.name.lowercase()}"

    companion object {
        const val MODE_EXACT = "exact"
        const val MODE_INEXACT = "inexact"
        const val STATE_PENDING = "pending"
        const val STATE_SCHEDULED = "scheduled"
        const val STATE_SUPPRESSED = "suppressed"
        const val STATE_FAILED = "failed"
        const val FAILURE_PERMISSION = "notification_permission"
        const val FAILURE_ALARM = "alarm_registration"
        const val FAILURE_INVALID_PREFIX = "invalid_notification_"
    }
}

internal sealed interface NotificationPlan {
    val notification: TodoNotification
    val logicalDate: LocalDate

    data class Candidate(val value: com.mochisofts.mata.domain.model.NotificationCandidate) : NotificationPlan {
        override val notification: TodoNotification = value.notification
        override val logicalDate: LocalDate = value.logicalDate
    }

    data class Invalid(
        override val notification: TodoNotification,
        override val logicalDate: LocalDate,
        val failureCode: String,
    ) : NotificationPlan
}

interface NotificationSystemStateProvider {
    fun current(): NotificationSystemState
}

@Singleton
class AndroidNotificationSystemStateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationSystemStateProvider {
    override fun current(): NotificationSystemState {
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channelEnabled = manager.getNotificationChannel(NotificationChannels.CHANNEL_ID)
            ?.importance != NotificationManager.IMPORTANCE_NONE
        val canPost = runtimePermissionGranted &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() && channelEnabled
        val exactRelevant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val canExact = !exactRelevant || context.getSystemService(AlarmManager::class.java)
            .canScheduleExactAlarms()
        return NotificationSystemState(
            canPostNotifications = canPost,
            runtimePermissionRelevant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            runtimePermissionGranted = runtimePermissionGranted,
            exactAlarmRelevant = exactRelevant,
            canScheduleExactAlarms = canExact,
        )
    }
}

internal enum class AlarmReconciliationMode {
    INCREMENTAL,
    REBUILD_OS_REGISTRATIONS,
}

internal fun canReuseScheduledAlarm(
    previous: ScheduledNotificationEntity?,
    desiredTriggerAt: Long,
    desiredMode: String,
    canPostNotifications: Boolean,
    reconciliationMode: AlarmReconciliationMode,
): Boolean =
    reconciliationMode == AlarmReconciliationMode.INCREMENTAL &&
        previous?.state == AndroidNotificationScheduler.STATE_SCHEDULED &&
        previous.triggerAt == desiredTriggerAt &&
        previous.schedulingMode == desiredMode &&
        canPostNotifications

@Singleton
class AndroidAlarmGateway @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmGateway {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(
        candidateKey: String,
        requestCode: Int,
        triggerAtMillis: Long,
        exact: Boolean,
    ) {
        val pendingIntent = pendingIntent(candidateKey, requestCode)
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    override fun cancel(candidateKey: String, requestCode: Int) {
        alarmManager.cancel(pendingIntent(candidateKey, requestCode))
    }

    private fun pendingIntent(candidateKey: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationAlarmReceiver::class.java)
                .setAction(NotificationAlarmReceiver.ACTION_FIRE)
                .putExtra(NotificationAlarmReceiver.EXTRA_CANDIDATE_KEY, candidateKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
