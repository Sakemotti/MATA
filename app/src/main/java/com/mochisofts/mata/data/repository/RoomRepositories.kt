package com.mochisofts.mata.data.repository

import androidx.room.withTransaction
import com.mochisofts.mata.core.common.ValidationError
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
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
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.occursOn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.repository.CategoryRepository
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
                ),
            )
        }
        categoryId
    }

    override suspend fun deleteCategory(id: String): Result<Unit> = runCatching {
        database.withTransaction {
            categoryDao.findById(id)?.let { categoryDao.delete(it) }
        }
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
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : TodoRepository {
    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> =
        combine(
            todoDao.observeActive(),
            categoryDao.observeAll(),
            executionDao.observeAll(),
            settingsRepository.uncategorizedEndHour,
            settingsRepository.weekStart,
        ) { todoEntities, categoryEntities, executionEntities, uncategorizedEndHour, weekStart ->
            val categories = categoryEntities.associateBy(CategoryEntity::id)
            val executions = executionEntities.associateBy { it.todoId to it.logicalDate }
            val executionsByTodo = executionEntities.groupBy(TodoExecutionEntity::todoId)
            val now = ZonedDateTime.now(clock)
            val today = now.toLocalDate()

            todoEntities.mapNotNull { entity ->
                val todo = entity.toDomain()
                val categoryEntity = entity.categoryId?.let(categories::get)
                val endHour = categoryEntity?.endHour ?: uncategorizedEndHour
                val targetDate = if (selectedDate == today) {
                    logicalDate(now, endHour)
                } else {
                    selectedDate
                }
                if (!todo.occursOn(targetDate)) return@mapNotNull null
                val execution = executions[todo.id to targetDate.toString()]
                val progress = todo.recurrencePeriod(targetDate, weekStart)?.let { period ->
                    val completedCount = executionsByTodo[todo.id].orEmpty().count { item ->
                        TodoState.fromStoredValue(item.state) == TodoState.COMPLETED &&
                            LocalDate.parse(item.logicalDate) in period.startDate..period.endDate
                    }
                    RecurrenceProgress(period, completedCount)
                }
                if (execution == null && progress?.isAchieved == true) return@mapNotNull null
                TodoOccurrence(
                    todo = todo,
                    category = categoryEntity?.toDomain(),
                    logicalDate = targetDate,
                    state = execution?.let { TodoState.fromStoredValue(it.state) } ?: TodoState.PENDING,
                    progress = progress,
                )
            }.sortedWith(
                compareBy<TodoOccurrence> { occurrence -> occurrence.effectiveDueMinutes() }
                    .thenBy { it.category?.sortOrder ?: -1 }
                    .thenBy { it.todo.createdAt },
            )
        }

    override fun observeTodos(): Flow<List<Todo>> =
        todoDao.observeActive().map { entities -> entities.map(TodoEntity::toDomain) }

    override suspend fun getTodo(id: String): Todo? = todoDao.findById(id)?.toDomain()

    override suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
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

        val todoId = id ?: UUID.randomUUID().toString()
        val encodedRule = RecurrenceRuleJson.encode(recurrenceRule)
        database.withTransaction {
            val existing = id?.let { todoDao.findById(it) }
            val now = clock.millis()
            todoDao.upsert(
                TodoEntity(
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
                ),
            )
        }
        todoId
    }

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
    ): Result<Unit> = runCatching {
        val weekStart = settingsRepository.weekStart.first()
        database.withTransaction {
            val todoEntity = todoDao.findById(todoId)
            if (todoEntity == null) {
                throw ValidationException(ValidationError.TODO_NOT_FOUND)
            }
            if (completed) {
                if (executionDao.find(todoId, logicalDate.toString()) != null) {
                    throw ValidationException(ValidationError.TODO_ALREADY_ACTED)
                }
                val todo = todoEntity.toDomain()
                todo.recurrencePeriod(logicalDate, weekStart)?.let { period ->
                    val completedCount = executionDao.findForTodo(todoId).count { execution ->
                        TodoState.fromStoredValue(execution.state) == TodoState.COMPLETED &&
                            LocalDate.parse(execution.logicalDate) in period.startDate..period.endDate
                    }
                    if (completedCount >= period.requiredCount) {
                        throw ValidationException(ValidationError.TODO_REQUIRED_COUNT_REACHED)
                    }
                }
                executionDao.upsert(
                    TodoExecutionEntity(
                        todoId = todoId,
                        logicalDate = logicalDate.toString(),
                        state = TodoState.COMPLETED.code,
                        performedAt = clock.millis(),
                    ),
                )
            } else {
                executionDao.delete(todoId, logicalDate.toString())
            }
        }
    }

    override suspend fun deleteTodo(id: String): Result<Unit> = runCatching {
        database.withTransaction { todoDao.deleteById(id) }
    }

    private fun TodoOccurrence.effectiveDueMinutes(): Int {
        val endHour = category?.endHour ?: 0
        val due = todo.dueMinutes ?: (endHour * 60)
        return due + if (due < endHour * 60 || todo.dueMinutes == null) 1440 else 0
    }
}

private fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    colorIndex = colorIndex,
    iconName = iconName,
    endHour = endHour,
    sortOrder = sortOrder,
)

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

private fun validate(condition: Boolean, error: ValidationError) {
    if (!condition) throw ValidationException(error)
}
