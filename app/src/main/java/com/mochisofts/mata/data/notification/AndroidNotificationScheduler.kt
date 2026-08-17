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
import com.mochisofts.mata.app.notification.NotificationReconcileReceiver
import com.mochisofts.mata.core.notification.AlarmGateway
import com.mochisofts.mata.data.local.CategoryDao
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
import com.mochisofts.mata.domain.model.nextNotificationCandidate
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
    private val categoryDao: CategoryDao,
    private val executionDao: TodoExecutionDao,
    private val notificationDao: TodoNotificationDao,
    private val scheduledDao: ScheduledNotificationDao,
    private val settingsRepository: SettingsRepository,
    private val holidayRepository: HolidayRepository,
    private val alarmGateway: AlarmGateway,
    private val clock: Clock,
) : NotificationScheduler {
    private val mutex = Mutex()

    override val notificationCount: Flow<Int> = notificationDao.observeCount()

    override fun systemState(): NotificationSystemState {
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

    override suspend fun reconcileTodo(todoId: String) {
        mutex.withLock {
            reconcileTodoLocked(todoId, holidayRepository.currentSnapshot().dates)
            updateReconcileReceiver()
        }
    }

    override suspend fun reconcileAll() {
        mutex.withLock {
            val holidays = holidayRepository.currentSnapshot().dates
            val activeTodos = todoDao.findActiveWithNotifications()
            val activeIds = activeTodos.mapTo(mutableSetOf(), TodoEntity::id)
            scheduledDao.findTodoIds().filterNot(activeIds::contains).forEach { todoId ->
                cancelTodoLocked(todoId)
            }
            activeTodos.forEach { reconcileTodoLocked(it.id, holidays) }
            updateReconcileReceiver()
        }
    }

    override suspend fun cancelTodo(todoId: String) {
        mutex.withLock {
            cancelTodoLocked(todoId)
            updateReconcileReceiver()
        }
    }

    private suspend fun reconcileTodoLocked(todoId: String, holidays: Set<LocalDate>) {
        val entity = todoDao.findById(todoId)
        val notificationEntities = notificationDao.findForTodo(todoId)
        if (entity == null || entity.archivedAt != null || notificationEntities.isEmpty()) {
            cancelTodoLocked(todoId)
            return
        }

        val todo = entity.toDomain()
        val category = entity.categoryId?.let { categoryDao.findById(it) }
        val endHour = category?.endHour ?: settingsRepository.uncategorizedEndHour.first()
        val weekStart = settingsRepository.weekStart.first()
        val executions = executionDao.findForTodo(todoId)
        val completedDates = executions.filter { TodoState.fromStoredValue(it.status) == TodoState.COMPLETED }
            .mapTo(mutableSetOf()) { LocalDate.parse(it.logicalDate) }
        val actedDates = executions.mapTo(mutableSetOf()) { LocalDate.parse(it.logicalDate) }
        val now = ZonedDateTime.now(clock)
        val desiredCandidates = notificationEntities.mapNotNull { setting ->
            nextNotificationCandidate(
                todo = todo,
                notification = setting.toDomain(),
                endHour = endHour,
                now = now,
                weekStart = weekStart,
                completedDates = completedDates,
                actedDates = actedDates,
                holidays = holidays,
            )
        }
        val desiredKeys = desiredCandidates.mapTo(mutableSetOf()) { candidate ->
            candidateKey(todo, candidate.notification, candidate.logicalDate)
        }
        val existing = scheduledDao.findForTodo(todoId)
        existing.filterNot { it.candidateKey in desiredKeys }.forEach { stale ->
            alarmGateway.cancel(stale.candidateKey, stale.requestCode)
            scheduledDao.delete(stale.candidateKey)
        }

        val state = systemState()
        var nextRequestCode = scheduledDao.maxRequestCode() + 1
        desiredCandidates.forEach { candidate ->
            val key = candidateKey(todo, candidate.notification, candidate.logicalDate)
            val previous = existing.firstOrNull { it.candidateKey == key }
            val exact = state.canScheduleExactAlarms
            val desiredMode = if (exact) MODE_EXACT else MODE_INEXACT
            if (
                previous?.state == STATE_SCHEDULED &&
                previous.triggerAt == candidate.triggerAt.toInstant().toEpochMilli() &&
                previous.schedulingMode == desiredMode &&
                state.canPostNotifications
            ) {
                return@forEach
            }
            previous?.let { alarmGateway.cancel(it.candidateKey, it.requestCode) }
            val requestCode = previous?.requestCode ?: nextRequestCode++
            val createdAt = previous?.createdAt ?: clock.millis()
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
                alarmGateway.schedule(key, requestCode, record.triggerAt, exact)
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

    private suspend fun cancelTodoLocked(todoId: String) {
        scheduledDao.findForTodo(todoId).forEach { scheduled ->
            alarmGateway.cancel(scheduled.candidateKey, scheduled.requestCode)
        }
        scheduledDao.deleteForTodo(todoId)
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

    companion object {
        const val MODE_EXACT = "exact"
        const val MODE_INEXACT = "inexact"
        const val STATE_PENDING = "pending"
        const val STATE_SCHEDULED = "scheduled"
        const val STATE_SUPPRESSED = "suppressed"
        const val STATE_FAILED = "failed"
        const val FAILURE_PERMISSION = "notification_permission"
        const val FAILURE_ALARM = "alarm_registration"
    }
}

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
