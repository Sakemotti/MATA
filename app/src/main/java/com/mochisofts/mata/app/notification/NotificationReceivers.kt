package com.mochisofts.mata.app.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MainActivity
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.ScheduledNotificationDao
import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.notification.AndroidNotificationScheduler
import com.mochisofts.mata.data.notification.NotificationChannels
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.RecurrenceProgress
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.deadlineAt
import com.mochisofts.mata.domain.model.logicalDayEnd
import com.mochisofts.mata.domain.model.occursOn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var deliveryService: NotificationDeliveryService

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val candidateKey = intent.getStringExtra(EXTRA_CANDIDATE_KEY) ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                deliveryService.deliver(candidateKey)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.mochisofts.mata.action.FIRE_NOTIFICATION"
        const val EXTRA_CANDIDATE_KEY = "candidate_key"
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var presenter: NotificationPresenter
    @Inject lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getStringExtra(EXTRA_TODO_ID) ?: return
        val date = intent.getStringExtra(EXTRA_LOGICAL_DATE)?.let(LocalDate::parse) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1).takeIf { it >= 0 } ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> complete(todoId, date, notificationId)
                    ACTION_UNDO -> undo(
                        todoId = todoId,
                        date = date,
                        notificationId = notificationId,
                        completedAt = intent.getLongExtra(EXTRA_COMPLETED_AT, 0L),
                    )
                    ACTION_DISMISS -> {
                        notificationScheduler.reconcileTodo(todoId)
                        presenter.refreshGroupSummary()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun complete(todoId: String, date: LocalDate, notificationId: Int) {
        todoRepository.setCompleted(todoId, date, true)
            .onSuccess {
                val todo = todoRepository.getTodo(todoId)
                if (todo == null) {
                    presenter.cancel(notificationId)
                } else {
                    presenter.showCompleted(todo, date, notificationId, clock.millis())
                }
            }
            .onFailure { presenter.showCompletionFailed(notificationId) }
    }

    private suspend fun undo(
        todoId: String,
        date: LocalDate,
        notificationId: Int,
        completedAt: Long,
    ) {
        if (completedAt <= 0 || clock.millis() - completedAt > UNDO_WINDOW_MILLIS) {
            presenter.showUndoFailed(notificationId)
            return
        }
        todoRepository.setCompleted(todoId, date, false)
            .onSuccess { presenter.cancel(notificationId) }
            .onFailure { presenter.showUndoFailed(notificationId) }
    }

    companion object {
        const val ACTION_COMPLETE = "com.mochisofts.mata.action.COMPLETE_FROM_NOTIFICATION"
        const val ACTION_UNDO = "com.mochisofts.mata.action.UNDO_FROM_NOTIFICATION"
        const val ACTION_DISMISS = "com.mochisofts.mata.action.DISMISS_NOTIFICATION"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_LOGICAL_DATE = "logical_date"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_COMPLETED_AT = "completed_at"
        private const val UNDO_WINDOW_MILLIS = 15_000L
    }
}

@AndroidEntryPoint
class NotificationReconcileReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: NotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                scheduler.reconcileAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@Singleton
class NotificationDeliveryService @Inject constructor(
    private val scheduledDao: ScheduledNotificationDao,
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val executionDao: TodoExecutionDao,
    private val notificationDao: TodoNotificationDao,
    private val settingsRepository: SettingsRepository,
    private val holidayRepository: HolidayRepository,
    private val scheduler: NotificationScheduler,
    private val presenter: NotificationPresenter,
    private val clock: Clock,
) {
    suspend fun deliver(candidateKey: String) {
        val scheduled = scheduledDao.find(candidateKey) ?: return
        if (scheduled.state != AndroidNotificationScheduler.STATE_SCHEDULED) return
        val entity = todoDao.findById(scheduled.todoId)
        val setting = notificationDao.find(scheduled.todoId, scheduled.notificationSettingId)
        if (entity == null || entity.archivedAt != null || setting == null ||
            entity.definitionRevision != scheduled.definitionRevision
        ) {
            suppress(scheduled, "stale_definition")
            return
        }

        val date = LocalDate.parse(scheduled.logicalDate)
        val todo = Todo(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            categoryId = entity.categoryId,
            startDate = LocalDate.parse(entity.startDate),
            endDate = entity.endDate?.let(LocalDate::parse),
            recurrenceRule = RecurrenceRuleJson.decode(
                entity.recurrenceType,
                entity.repeatParamsVersion,
                entity.repeatParamsJson,
            ),
            dueMinutes = entity.dueMinutes,
            definitionRevision = entity.definitionRevision,
            archivedAt = entity.archivedAt,
            createdAt = entity.createdAt,
        )
        val execution = executionDao.find(todo.id, date.toString())
        val holidays = holidayRepository.currentSnapshot().dates
        if (!todo.occursOn(date, holidays) || execution != null ||
            !scheduler.systemState().canPostNotifications
        ) {
            suppress(scheduled, "not_eligible")
            return
        }

        val category = entity.categoryId?.let { categoryDao.findById(it) }
        val endHour = category?.endHour ?: settingsRepository.uncategorizedEndHour.first()
        val weekStart = settingsRepository.weekStart.first()
        val now = ZonedDateTime.now(clock)
        val deadline = deadlineAt(date, endHour, todo.dueMinutes, now.zone)
        val dayEnd = logicalDayEnd(date, endHour, now.zone)
        val isBoundary = todo.dueMinutes == null &&
            NotificationRelation.fromStoredValue(setting.relation) == NotificationRelation.AT
        if (now.isAfter(dayEnd) && !(isBoundary && Duration.between(dayEnd, now).toMinutes() < 5)) {
            suppress(scheduled, "logical_day_ended")
            return
        }

        val progress = todo.recurrencePeriod(date, weekStart)?.let { period ->
            val count = executionDao.findForTodo(todo.id).count { item ->
                TodoState.fromStoredValue(item.status) == TodoState.COMPLETED &&
                    LocalDate.parse(item.logicalDate) in period.startDate..period.endDate
            }
            RecurrenceProgress(period, count)
        }
        presenter.showTodo(
            scheduled = scheduled,
            todo = todo,
            categoryName = category?.name,
            categoryColorIndex = category?.colorIndex,
            logicalDate = date,
            deadline = deadline,
            now = now,
            isBoundary = isBoundary,
            progress = progress,
        )
        scheduledDao.upsert(
            scheduled.copy(
                state = "delivered",
                failureCode = null,
                updatedAt = clock.millis(),
            ),
        )
        scheduler.reconcileTodo(todo.id)
    }

    private suspend fun suppress(scheduled: ScheduledNotificationEntity, reason: String) {
        scheduledDao.upsert(
            scheduled.copy(state = "suppressed", failureCode = reason, updatedAt = clock.millis()),
        )
        scheduler.reconcileTodo(scheduled.todoId)
    }
}

@Singleton
class NotificationPresenter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun showTodo(
        scheduled: ScheduledNotificationEntity,
        todo: Todo,
        categoryName: String?,
        categoryColorIndex: Int?,
        logicalDate: LocalDate,
        deadline: ZonedDateTime,
        now: ZonedDateTime,
        isBoundary: Boolean,
        progress: RecurrenceProgress?,
    ) {
        if (!canPost()) return
        val notificationId = scheduled.requestCode
        val body = buildList {
            add(categoryName ?: context.getString(R.string.label_uncategorized))
            add(logicalDate.format(JAPANESE_DATE_FORMATTER))
            add(deadlineText(deadline, now, isBoundary))
            progress?.let {
                add(context.getString(R.string.notification_progress_format, it.completedCount, it.period.requiredCount))
            }
        }.joinToString("・")
        val publicVersion = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_public_text))
            .build()
        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(todo.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(CATEGORY_COLORS[categoryColorIndex?.coerceIn(0, 15) ?: DEFAULT_COLOR_INDEX])
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(contentIntent(todo.id, logicalDate, scheduled.candidateKey, notificationId))
            .setDeleteIntent(actionIntent(NotificationActionReceiver.ACTION_DISMISS, todo.id, logicalDate, notificationId))
        if (!isBoundary) {
            builder.addAction(
                0,
                context.getString(R.string.action_complete),
                actionIntent(NotificationActionReceiver.ACTION_COMPLETE, todo.id, logicalDate, notificationId),
            )
        }
        manager.notify(NOTIFICATION_TAG, notificationId, builder.build())
        updateGroupSummary()
    }

    @SuppressLint("MissingPermission")
    fun showCompleted(todo: Todo, date: LocalDate, notificationId: Int, completedAt: Long) {
        if (!canPost()) return
        val undoIntent = actionIntent(
            action = NotificationActionReceiver.ACTION_UNDO,
            todoId = todo.id,
            date = date,
            notificationId = notificationId,
            completedAt = completedAt,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(todo.title)
            .setContentText(context.getString(R.string.notification_completed))
            .setTimeoutAfter(15_000L)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP_KEY)
            .setDeleteIntent(
                actionIntent(NotificationActionReceiver.ACTION_DISMISS, todo.id, date, notificationId),
            )
            .addAction(0, context.getString(R.string.action_undo), undoIntent)
            .build()
        manager.notify(NOTIFICATION_TAG, notificationId, notification)
        updateGroupSummary()
    }

    @SuppressLint("MissingPermission")
    fun showCompletionFailed(notificationId: Int) = showFailure(
        notificationId,
        R.string.notification_completion_failed,
    )

    @SuppressLint("MissingPermission")
    fun showUndoFailed(notificationId: Int) = showFailure(
        notificationId,
        R.string.notification_undo_failed,
    )

    fun cancel(notificationId: Int) {
        manager.cancel(NOTIFICATION_TAG, notificationId)
        updateGroupSummary()
    }

    fun refreshGroupSummary() {
        updateGroupSummary()
    }

    @SuppressLint("MissingPermission")
    private fun showFailure(notificationId: Int, messageRes: Int) {
        if (!canPost()) return
        manager.notify(
            NOTIFICATION_TAG,
            notificationId,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(messageRes))
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateGroupSummary() {
        if (!canPost()) return
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val count = notificationManager.activeNotifications.count { it.tag == NOTIFICATION_TAG }
        if (count <= 1) {
            manager.cancel(SUMMARY_TAG, SUMMARY_ID)
            return
        }
        manager.notify(
            SUMMARY_TAG,
            SUMMARY_ID,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_group_title))
                .setContentText(context.resources.getQuantityString(R.plurals.notification_group_count, count, count))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .build(),
        )
    }

    private fun deadlineText(deadline: ZonedDateTime, now: ZonedDateTime, boundary: Boolean): String {
        if (boundary) return context.getString(R.string.notification_day_boundary)
        val minutes = Duration.between(now, deadline).toMinutes()
        if (minutes == 0L) return context.getString(R.string.notification_due_now)
        val absolute = kotlin.math.abs(minutes)
        val quantity = when {
            absolute % 1_440 == 0L -> context.getString(R.string.notification_day_count, absolute / 1_440)
            absolute % 60 == 0L -> context.getString(R.string.notification_hour_count, absolute / 60)
            else -> context.getString(R.string.notification_minute_count, absolute)
        }
        return if (minutes > 0) {
            context.getString(R.string.notification_before_text, quantity)
        } else {
            context.getString(R.string.notification_after_text, quantity)
        }
    }

    private fun contentIntent(
        todoId: String,
        date: LocalDate,
        candidateKey: String,
        notificationId: Int,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        purposeRequestCode(notificationId, 1),
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_NOTIFICATION)
            .putExtra(MainActivity.EXTRA_TODO_ID, todoId)
            .putExtra(MainActivity.EXTRA_LOGICAL_DATE, date.toString())
            .putExtra(MainActivity.EXTRA_CANDIDATE_KEY, candidateKey)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(
        action: String,
        todoId: String,
        date: LocalDate,
        notificationId: Int,
        completedAt: Long = 0L,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        purposeRequestCode(notificationId, if (action == NotificationActionReceiver.ACTION_COMPLETE) 2 else 3),
        Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_TODO_ID, todoId)
            .putExtra(NotificationActionReceiver.EXTRA_LOGICAL_DATE, date.toString())
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            .putExtra(NotificationActionReceiver.EXTRA_COMPLETED_AT, completedAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPost(): Boolean =
        (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled()

    private fun purposeRequestCode(base: Int, purpose: Int): Int = base * 4 + purpose

    private companion object {
        const val GROUP_KEY = "mata_todo_reminders"
        const val NOTIFICATION_TAG = "mata_todo"
        const val SUMMARY_TAG = "mata_summary"
        const val SUMMARY_ID = 9_001
        const val DEFAULT_COLOR_INDEX = 8
        val JAPANESE_DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE)
        val CATEGORY_COLORS = intArrayOf(
            0xFFC62828.toInt(), 0xFFAD1457.toInt(), 0xFF6A1B9A.toInt(), 0xFF283593.toInt(),
            0xFF1565C0.toInt(), 0xFF0277BD.toInt(), 0xFF00838F.toInt(), 0xFF00796B.toInt(),
            0xFF2E7D32.toInt(), 0xFF558B2F.toInt(), 0xFF827717.toInt(), 0xFFF9A825.toInt(),
            0xFFEF6C00.toInt(), 0xFFD84315.toInt(), 0xFF5D4037.toInt(), 0xFF546E7A.toInt(),
        )
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
