package com.mochisofts.mata.data.repository

import androidx.room.withTransaction
import com.mochisofts.mata.core.common.ValidationError
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateDao
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.RecurrenceProgress
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.NotificationValidationError
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.occursOn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.model.validateNotifications
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val database: MataDatabase,
    private val categoryDao: CategoryDao,
    private val clock: Clock,
    private val notificationScheduler: NotificationScheduler,
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map(CategoryEntity::toDomain) }

    override suspend fun getCategory(id: String): Category? = categoryDao.findById(id)?.toDomain()

    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
        endHour: Int,
    ): Result<String> = runCatching {
        val trimmedName = name.trim()
        validate(trimmedName.isNotEmpty(), ValidationError.CATEGORY_NAME_REQUIRED)
        validate(trimmedName.length <= 30, ValidationError.CATEGORY_NAME_TOO_LONG)
        validate(colorIndex in 0..15, ValidationError.CATEGORY_COLOR_INVALID)
        validate(endHour in 0..23, ValidationError.CATEGORY_END_HOUR_INVALID)

        val categoryId = id ?: UUID.randomUUID().toString()
        val normalizedName = normalizeName(trimmedName)
        database.withTransaction {
            if (categoryDao.findDuplicate(normalizedName, categoryId) != null) {
                throw ValidationException(ValidationError.CATEGORY_NAME_DUPLICATE)
            }
            val existing = id?.let { categoryDao.findById(it) }
            categoryDao.upsert(
                CategoryEntity(
                    id = categoryId,
                    name = trimmedName,
                    normalizedName = normalizedName,
                    colorIndex = colorIndex,
                    iconName = iconName,
                    endHour = endHour,
                    sortOrder = existing?.sortOrder ?: categoryDao.nextSortOrder(),
                    createdAt = existing?.createdAt ?: clock.millis(),
                    updatedAt = clock.millis(),
                ),
            )
        }
        runCatching { notificationScheduler.reconcileAll() }
        categoryId
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        database.withTransaction {
            categoryDao.findById(id)?.let { categoryDao.delete(it) }
        }
        runCatching { notificationScheduler.reconcileAll() }
    }

    private fun normalizeName(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}

@Singleton
class RoomTodoRepository @Inject constructor(
    private val database: MataDatabase,
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val executionDao: TodoExecutionDao,
    private val notificationDao: TodoNotificationDao,
    private val runtimeStateDao: TodoRuntimeStateDao,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val holidayRepository: HolidayRepository,
    private val clock: Clock,
) : TodoRepository {
    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> {
        val inputs = combine(
            todoDao.observeActive(),
            categoryDao.observeAll(),
            executionDao.observeAll(),
            settingsRepository.uncategorizedEndHour,
            settingsRepository.weekStart,
        ) { todoEntities, categoryEntities, executionEntities, uncategorizedEndHour, weekStart ->
            OccurrenceInputs(
                todos = todoEntities,
                categories = categoryEntities,
                executions = executionEntities,
                uncategorizedEndHour = uncategorizedEndHour,
                weekStart = weekStart,
            )
        }
        return combine(inputs, holidayRepository.snapshot) { input, holidaySnapshot ->
            val categories = input.categories.associateBy(CategoryEntity::id)
            val executions = input.executions.associateBy { it.todoId to it.logicalDate }
            val executionsByTodo = input.executions.groupBy(TodoExecutionEntity::todoId)
            val now = ZonedDateTime.now(clock)
            val today = now.toLocalDate()

            input.todos.mapNotNull { entity ->
                val todo = entity.toDomain()
                val categoryEntity = entity.categoryId?.let(categories::get)
                val endHour = categoryEntity?.endHour ?: input.uncategorizedEndHour
                val targetDate = if (selectedDate == today) {
                    logicalDate(now, endHour)
                } else {
                    selectedDate
                }
                val execution = executions[todo.id to targetDate.toString()]
                if (!todo.occursOn(targetDate, holidaySnapshot.dates) && execution == null) {
                    return@mapNotNull null
                }
                val progress = todo.recurrencePeriod(targetDate, input.weekStart)?.let { period ->
                    val completedCount = executionsByTodo[todo.id].orEmpty().count { item ->
                        TodoState.fromStoredValue(item.status) == TodoState.COMPLETED &&
                            LocalDate.parse(item.logicalDate) in period.startDate..period.endDate
                    }
                    RecurrenceProgress(period, completedCount)
                }
                if (execution == null && progress?.isAchieved == true) return@mapNotNull null
                TodoOccurrence(
                    todo = todo,
                    category = categoryEntity?.toDomain(),
                    logicalDate = targetDate,
                    state = execution?.let { TodoState.fromStoredValue(it.status) } ?: TodoState.PENDING,
                    progress = progress,
                )
            }.sortedWith(
                compareBy<TodoOccurrence> { occurrence -> occurrence.effectiveDueMinutes() }
                    .thenBy { it.category?.sortOrder ?: -1 }
                    .thenBy { it.todo.createdAt },
            )
        }
    }

    private data class OccurrenceInputs(
        val todos: List<TodoEntity>,
        val categories: List<CategoryEntity>,
        val executions: List<TodoExecutionEntity>,
        val uncategorizedEndHour: Int,
        val weekStart: java.time.DayOfWeek,
    )

    override fun observeTodos(): Flow<List<Todo>> =
        todoDao.observeActive().map { entities -> entities.map(TodoEntity::toDomain) }

    override suspend fun getTodo(id: String): Todo? = todoDao.findById(id)?.let { entity ->
        entity.toDomain(
            notifications = notificationDao.findForTodo(id).map(TodoNotificationEntity::toDomain),
        )
    }

    override suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        notifications: List<TodoNotification>,
    ): Result<String> = runCatching {
        val trimmedTitle = title.trim()
        validate(trimmedTitle.isNotEmpty(), ValidationError.TODO_TITLE_REQUIRED)
        validate(trimmedTitle.length <= 100, ValidationError.TODO_TITLE_TOO_LONG)
        validate(description.length <= 1000, ValidationError.TODO_DESCRIPTION_TOO_LONG)
        validate(dueMinutes == null || dueMinutes in 0..1439, ValidationError.TODO_DUE_TIME_INVALID)
        validate(recurrenceRule.isValid(), ValidationError.TODO_RECURRENCE_RULE_INVALID)
        validate(endDate == null || !endDate.isBefore(startDate), ValidationError.TODO_END_DATE_BEFORE_START)
        val category = categoryId?.let { categoryDao.findById(it) }
        if (categoryId != null && category == null) {
            throw ValidationException(ValidationError.TODO_CATEGORY_NOT_FOUND)
        }
        if (id == null) {
            val endHour = category?.endHour ?: settingsRepository.uncategorizedEndHour.first()
            val currentLogicalDate = logicalDate(ZonedDateTime.now(clock), endHour)
            validate(!startDate.isBefore(currentLogicalDate), ValidationError.TODO_DATE_IN_PAST)
        }

        val endHour = category?.endHour ?: settingsRepository.uncategorizedEndHour.first()
        when (validateNotifications(notifications, dueMinutes, endHour).firstOrNull()) {
            NotificationValidationError.TOO_MANY ->
                throw ValidationException(ValidationError.TODO_NOTIFICATION_TOO_MANY)
            NotificationValidationError.INVALID_AMOUNT ->
                throw ValidationException(ValidationError.TODO_NOTIFICATION_AMOUNT_INVALID)
            NotificationValidationError.DUPLICATE ->
                throw ValidationException(ValidationError.TODO_NOTIFICATION_DUPLICATE)
            NotificationValidationError.AFTER_REQUIRES_DEADLINE ->
                throw ValidationException(ValidationError.TODO_NOTIFICATION_AFTER_REQUIRES_DEADLINE)
            NotificationValidationError.AFTER_DAY_END ->
                throw ValidationException(ValidationError.TODO_NOTIFICATION_AFTER_DAY_END)
            null -> Unit
        }

        val todoId = id ?: UUID.randomUUID().toString()
        val encodedRule = RecurrenceRuleJson.encode(recurrenceRule)
        database.withTransaction {
            val existing = id?.let { todoDao.findById(it) }
            val now = clock.millis()
            val updatedTodo = TodoEntity(
                    id = todoId,
                    title = trimmedTitle,
                    description = description,
                    categoryId = categoryId,
                    startDate = startDate.toString(),
                    endDate = if (recurrenceRule.type == RecurrenceType.ONCE) {
                        startDate.toString()
                    } else {
                        endDate?.toString()
                    },
                    recurrenceType = encodedRule.typeCode,
                    repeatParamsVersion = encodedRule.paramsVersion,
                    repeatParamsJson = encodedRule.paramsJson,
                    dueMinutes = dueMinutes,
                    definitionRevision = (existing?.definitionRevision ?: 0) + 1,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    archivedAt = existing?.archivedAt,
                )
            todoDao.upsert(updatedTodo)
            val existingNotifications = notificationDao.findForTodo(todoId).associateBy { it.id }
            notificationDao.deleteForTodo(todoId)
            notificationDao.upsertAll(
                notifications.mapIndexed { index, notification ->
                    val existingNotification = existingNotifications[notification.id]
                    TodoNotificationEntity(
                        id = notification.id,
                        todoId = todoId,
                        relation = notification.relation.code,
                        amount = notification.amount,
                        unit = notification.unit.code,
                        sortOrder = index,
                        createdAt = existingNotification?.createdAt ?: now,
                        updatedAt = now,
                    )
                },
            )
            val existingRuntime = runtimeStateDao.find(todoId)
            runtimeStateDao.upsert(
                TodoRuntimeStateEntity(
                    todoId = todoId,
                    lastFinalizedLogicalDate = existingRuntime?.lastFinalizedLogicalDate
                        ?: startDate.minusDays(1).toString(),
                    lastFinalizedWeeklyPeriodEnd = existingRuntime?.lastFinalizedWeeklyPeriodEnd,
                    lastFinalizedMonthlyPeriodEnd = existingRuntime?.lastFinalizedMonthlyPeriodEnd,
                    appliedDefinitionRevision = updatedTodo.definitionRevision,
                    reconciliationCursorDate = existingRuntime?.reconciliationCursorDate,
                    updatedAt = now,
                ),
            )
        }
        runCatching { notificationScheduler.reconcileTodo(todoId) }
        todoId
    }

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
    ): Result<Unit> = runCatching {
        val weekStart = settingsRepository.weekStart.first()
        val uncategorizedEndHour = settingsRepository.uncategorizedEndHour.first()
        val holidays = holidayRepository.currentSnapshot().dates
        val now = ZonedDateTime.now(clock)
        database.withTransaction {
            val todoEntity = todoDao.findById(todoId)
                ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            val category = todoEntity.categoryId?.let { categoryDao.findById(it) }
            val endHour = category?.endHour ?: uncategorizedEndHour
            validateActionTarget(todoEntity, logicalDate, endHour, now, holidays)
            if (completed) {
                if (executionDao.findByOperationId(operationId) != null) return@withTransaction
                val existingExecution = executionDao.find(todoId, logicalDate.toString())
                if (existingExecution?.let { TodoState.fromStoredValue(it.status) } == TodoState.COMPLETED) {
                    return@withTransaction
                }
                if (existingExecution != null) {
                    throw ValidationException(ValidationError.TODO_ALREADY_ACTED)
                }
                val todo = todoEntity.toDomain()
                todo.recurrencePeriod(logicalDate, weekStart)?.let { period ->
                    val completedCount = executionDao.findForTodo(todoId).count { execution ->
                        TodoState.fromStoredValue(execution.status) == TodoState.COMPLETED &&
                            LocalDate.parse(execution.logicalDate) in period.startDate..period.endDate
                    }
                    if (completedCount >= period.requiredCount) {
                        throw ValidationException(ValidationError.TODO_REQUIRED_COUNT_REACHED)
                    }
                }
                executionDao.insert(
                    createExecution(
                        todo = todoEntity,
                        category = category,
                        logicalDate = logicalDate,
                        status = TodoState.COMPLETED,
                        operationId = operationId,
                        endHour = endHour,
                        weekStart = weekStart,
                    ),
                )
            } else {
                val existingExecution = executionDao.find(todoId, logicalDate.toString())
                if (existingExecution == null ||
                    TodoState.fromStoredValue(existingExecution.status) != TodoState.COMPLETED
                ) {
                    throw ValidationException(ValidationError.TODO_ACTION_CANNOT_UNDO)
                }
                executionDao.delete(todoId, logicalDate.toString())
            }
            updateAppliedRevision(todoEntity)
        }
        runCatching { notificationScheduler.reconcileTodo(todoId) }
    }

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
    ): Result<Unit> = runCatching {
        val weekStart = settingsRepository.weekStart.first()
        val uncategorizedEndHour = settingsRepository.uncategorizedEndHour.first()
        val holidays = holidayRepository.currentSnapshot().dates
        val now = ZonedDateTime.now(clock)
        database.withTransaction {
            val todoEntity = todoDao.findById(todoId)
                ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            val category = todoEntity.categoryId?.let { categoryDao.findById(it) }
            val endHour = category?.endHour ?: uncategorizedEndHour
            validateActionTarget(todoEntity, logicalDate, endHour, now, holidays)
            if (skipped) {
                if (executionDao.findByOperationId(operationId) != null) return@withTransaction
                val existing = executionDao.find(todoId, logicalDate.toString())
                if (existing?.let { TodoState.fromStoredValue(it.status) } == TodoState.SKIPPED) {
                    return@withTransaction
                }
                if (existing != null) throw ValidationException(ValidationError.TODO_ALREADY_ACTED)
                executionDao.insert(
                    createExecution(
                        todo = todoEntity,
                        category = category,
                        logicalDate = logicalDate,
                        status = TodoState.SKIPPED,
                        operationId = operationId,
                        endHour = endHour,
                        weekStart = weekStart,
                    ),
                )
            } else {
                val existing = executionDao.find(todoId, logicalDate.toString())
                if (existing == null || TodoState.fromStoredValue(existing.status) != TodoState.SKIPPED) {
                    throw ValidationException(ValidationError.TODO_ACTION_CANNOT_UNDO)
                }
                executionDao.delete(todoId, logicalDate.toString())
            }
            updateAppliedRevision(todoEntity)
        }
        runCatching { notificationScheduler.reconcileTodo(todoId) }
    }

    override suspend fun undoCompletion(operationId: String): Result<Unit> = runCatching {
        val todoId = database.withTransaction {
            val execution = executionDao.findByOperationId(operationId)
                ?: throw ValidationException(ValidationError.TODO_ACTION_CANNOT_UNDO)
            if (TodoState.fromStoredValue(execution.status) != TodoState.COMPLETED) {
                throw ValidationException(ValidationError.TODO_ACTION_CANNOT_UNDO)
            }
            val todo = todoDao.findById(execution.todoId)
                ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            validate(todo.archivedAt == null, ValidationError.TODO_NOT_FOUND)
            executionDao.deleteById(execution.id)
            updateAppliedRevision(todo)
            todo.id
        }
        runCatching { notificationScheduler.reconcileTodo(todoId) }
    }

    override suspend fun archiveTodo(id: String): Result<Unit> = runCatching {
        database.withTransaction {
            val todo = todoDao.findById(id) ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            if (todo.archivedAt != null) return@withTransaction
            val now = clock.millis()
            todoDao.upsert(todo.copy(archivedAt = now, updatedAt = now))
            updateAppliedRevision(todo)
        }
        runCatching { notificationScheduler.cancelTodo(id) }
    }

    override suspend fun restoreTodo(id: String): Result<Unit> = runCatching {
        val weekStart = settingsRepository.weekStart.first()
        val uncategorizedEndHour = settingsRepository.uncategorizedEndHour.first()
        val now = ZonedDateTime.now(clock)
        database.withTransaction {
            val entity = todoDao.findById(id) ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            if (entity.archivedAt == null) return@withTransaction
            val category = entity.categoryId?.let { categoryDao.findById(it) }
            val endHour = category?.endHour ?: uncategorizedEndHour
            val currentLogicalDate = logicalDate(now, endHour)
            val todo = entity.toDomain()
            val currentPeriod = todo.recurrencePeriod(currentLogicalDate, weekStart)
            val existing = runtimeStateDao.find(id)
            val restored = entity.copy(archivedAt = null, updatedAt = clock.millis())
            todoDao.upsert(restored)
            runtimeStateDao.upsert(
                TodoRuntimeStateEntity(
                    todoId = id,
                    lastFinalizedLogicalDate = maxDateString(
                        existing?.lastFinalizedLogicalDate,
                        currentLogicalDate.minusDays(1),
                    ),
                    lastFinalizedWeeklyPeriodEnd = if (todo.recurrenceType == RecurrenceType.WEEKLY_COUNT) {
                        maxDateString(existing?.lastFinalizedWeeklyPeriodEnd, currentPeriod?.startDate?.minusDays(1))
                    } else {
                        existing?.lastFinalizedWeeklyPeriodEnd
                    },
                    lastFinalizedMonthlyPeriodEnd = if (todo.recurrenceType == RecurrenceType.MONTHLY_COUNT) {
                        maxDateString(existing?.lastFinalizedMonthlyPeriodEnd, currentPeriod?.startDate?.minusDays(1))
                    } else {
                        existing?.lastFinalizedMonthlyPeriodEnd
                    },
                    appliedDefinitionRevision = restored.definitionRevision,
                    reconciliationCursorDate = null,
                    updatedAt = clock.millis(),
                ),
            )
        }
        runCatching { notificationScheduler.reconcileTodo(id) }
    }

    override suspend fun deleteTodo(id: String): Result<Unit> = runCatching {
        database.withTransaction { todoDao.deleteById(id) }
        runCatching { notificationScheduler.cancelTodo(id) }
    }

    private suspend fun createExecution(
        todo: TodoEntity,
        category: CategoryEntity?,
        logicalDate: LocalDate,
        status: TodoState,
        operationId: String,
        endHour: Int,
        weekStart: java.time.DayOfWeek,
    ): TodoExecutionEntity {
        val timestamp = clock.millis()
        return TodoExecutionEntity(
            id = UUID.randomUUID().toString(),
            operationId = operationId,
            todoId = todo.id,
            logicalDate = logicalDate.toString(),
            status = status.code,
            actedAt = timestamp,
            finalizedAt = timestamp,
            definitionRevision = todo.definitionRevision,
            snapshotVersion = HistorySnapshotV1.VERSION,
            snapshotJson = HistorySnapshotJson.encode(
                todo = todo,
                category = category,
                notifications = notificationDao.findForTodo(todo.id),
                endHour = endHour,
                weekStart = weekStart,
                logicalDate = logicalDate,
            ),
        )
    }

    private fun validateActionTarget(
        entity: TodoEntity,
        date: LocalDate,
        endHour: Int,
        now: ZonedDateTime,
        holidays: Set<LocalDate>,
    ) {
        if (entity.archivedAt != null) throw ValidationException(ValidationError.TODO_NOT_ACTIVE)
        val todo = entity.toDomain()
        if (date != logicalDate(now, endHour) || !todo.occursOn(date, holidays)) {
            throw ValidationException(ValidationError.TODO_ACTION_DATE_INVALID)
        }
    }

    private suspend fun updateAppliedRevision(todo: TodoEntity) {
        val existing = runtimeStateDao.find(todo.id)
        runtimeStateDao.upsert(
            TodoRuntimeStateEntity(
                todoId = todo.id,
                lastFinalizedLogicalDate = existing?.lastFinalizedLogicalDate,
                lastFinalizedWeeklyPeriodEnd = existing?.lastFinalizedWeeklyPeriodEnd,
                lastFinalizedMonthlyPeriodEnd = existing?.lastFinalizedMonthlyPeriodEnd,
                appliedDefinitionRevision = todo.definitionRevision,
                reconciliationCursorDate = existing?.reconciliationCursorDate,
                updatedAt = clock.millis(),
            ),
        )
    }

    private fun TodoOccurrence.effectiveDueMinutes(): Int {
        val endHour = category?.endHour ?: 0
        val due = todo.dueMinutes ?: (endHour * 60)
        return due + if (due < endHour * 60 || todo.dueMinutes == null) 1440 else 0
    }
}

private fun maxDateString(existing: String?, candidate: LocalDate?): String? {
    if (candidate == null) return existing
    val current = existing?.let(LocalDate::parse)
    return maxOf(current ?: candidate, candidate).toString()
}

private fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    colorIndex = colorIndex,
    iconName = iconName,
    endHour = endHour,
    sortOrder = sortOrder,
)

internal fun TodoEntity.toDomain(
    notifications: List<TodoNotification> = emptyList(),
) = Todo(
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
    notifications = notifications,
)

private fun TodoNotificationEntity.toDomain() = TodoNotification(
    id = id,
    relation = NotificationRelation.fromStoredValue(relation),
    amount = amount,
    unit = NotificationUnit.fromStoredValue(unit),
)

private fun validate(condition: Boolean, error: ValidationError) {
    if (!condition) throw ValidationException(error)
}
