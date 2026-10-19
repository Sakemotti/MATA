package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.IncompleteTodoRegistration
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomIncompleteTodoRepositoryTest {
    private lateinit var db: MataDatabase
    private lateinit var repository: RoomTodoRepository
    private val today = LocalDate.of(2026, 10, 18)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java).build()
        repository = RoomTodoRepository(db, db.todoDao(), db.categoryDao(), db.todoExecutionDao(),
            db.todoNotificationDao(), db.todoRuntimeStateDao(), InboxSettings(), InboxScheduler(),
            WidgetUpdater(context, DiagnosticLogger()), TestHolidayRepository(),
            Clock.fixed(today.atTime(12, 0).atZone(ZoneId.of("Asia/Tokyo")).toInstant(), ZoneId.of("Asia/Tokyo")))
    }
    @After fun tearDown() { db.close() }

    private suspend fun seed(): TodoEntity {
        val todo = TodoEntity("original", "日課", "元の説明", null, "2026-09-01", null,
            "daily", 1, "{}", null, 2, 1, 2, null)
        db.todoDao().upsert(todo)
        (1..3).forEach { seedMiss(today.minusDays(it.toLong())) }
        return todo
    }

    private suspend fun seedMiss(date: LocalDate) {
        db.todoExecutionDao().insert(TodoExecutionEntity("e-$date", "op-$date", "original", date.toString(),
            "missed", null, 1, 2, 1, "{}"))
    }

    private fun registration(title: String = "やり直す日課") = IncompleteTodoRegistration(
        title, "新しい説明", null, today, null, RecurrenceRule.once(), null, emptyList(), null, false,
    )

    @Test fun allMissesAndTodayAreGroupedAndDismissingDoesNotMutateHistoryOrToday() = runBlocking {
        val original = seed()
        val history = db.todoExecutionDao().findForTodo(original.id)
        val group = repository.observeIncompleteTodos().first().single()
        assertEquals(4, group.count)
        assertEquals(3, group.pastCount)
        repository.dismissIncompleteTodos(group.source).getOrThrow()
        assertTrue(repository.observeIncompleteTodos().first().isEmpty())
        assertEquals(history, db.todoExecutionDao().findForTodo(original.id))
        assertEquals(original.copy(incompleteDismissedThroughDate = today.toString()), db.todoDao().findById(original.id))
        assertEquals(TodoState.PENDING, repository.observeOccurrences(today).first().single().state)
    }

    @Test fun reRegistrationCreatesExactlyOneNewTodoAndKeepsOriginalScheduleAndHistory() = runBlocking {
        val original = seed()
        val history = db.todoExecutionDao().findForTodo(original.id)
        val source = repository.observeIncompleteTodos().first().single().source
        val id = repository.registerIncompleteTodo(source, registration()).getOrThrow()
        assertNotEquals(original.id, id)
        val created = repository.getTodo(id)!!
        assertEquals(RecurrenceType.ONCE, created.recurrenceType)
        assertEquals(today, created.startDate)
        assertEquals("やり直す日課", created.title)
        assertNull(created.dueMinutes)
        assertTrue(created.notifications.isEmpty())
        assertEquals(history, db.todoExecutionDao().findForTodo(original.id))
        assertEquals(original.copy(incompleteDismissedThroughDate = today.toString()), db.todoDao().findById(original.id))
        assertTrue(repository.registerIncompleteTodo(source, registration()).isFailure)
        assertEquals(2, db.todoDao().backupCount())
    }

    @Test fun invalidRegistrationDoesNotAcknowledgeOriginal() = runBlocking {
        seed()
        val source = repository.observeIncompleteTodos().first().single().source
        assertTrue(repository.registerIncompleteTodo(source, registration("")).isFailure)
        assertNull(db.todoDao().findById(source.todoId)?.incompleteDismissedThroughDate)
        assertEquals(1, db.todoDao().backupCount())
    }

    @Test fun databaseFailureRollsBackAcknowledgementAndCreationTogether() = runBlocking {
        seed()
        val source = repository.observeIncompleteTodos().first().single().source
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_registration BEFORE INSERT ON todos
            WHEN NEW.title = 'fail' BEGIN SELECT RAISE(ABORT, 'injected'); END
        """.trimIndent())
        assertTrue(repository.registerIncompleteTodo(source, registration("fail")).isFailure)
        assertNull(db.todoDao().findById(source.todoId)?.incompleteDismissedThroughDate)
        assertEquals(1, db.todoDao().backupCount())
        assertEquals(4, repository.observeIncompleteTodos().first().single().count)
    }

    @Test fun normalDefinitionEditingPreservesAcknowledgement() = runBlocking {
        val original = seed()
        val source = repository.observeIncompleteTodos().first().single().source
        repository.dismissIncompleteTodos(source).getOrThrow()
        repository.saveTodo(original.id, "名前変更", original.description, null, LocalDate.parse(original.startDate),
            null, RecurrenceRule.daily(), null).getOrThrow()
        assertEquals(today.toString(), db.todoDao().findById(original.id)?.incompleteDismissedThroughDate)
        assertTrue(repository.observeIncompleteTodos().first().isEmpty())
    }

    @Test fun manyDatesCanBeReadInDistinctBoundedPages() = runBlocking {
        seed()
        (4..45).forEach { seedMiss(today.minusDays(it.toLong())) }
        val first = repository.observeIncompleteHistory("original", 20, 0).first()
        val second = repository.observeIncompleteHistory("original", 20, 20).first()
        val last = repository.observeIncompleteHistory("original", 20, 40).first()
        assertEquals(20, first.size)
        assertEquals(20, second.size)
        assertEquals(5, last.size)
        assertEquals(45, (first + second + last).map { it.date }.distinct().size)
        assertEquals(45, repository.observeIncompleteTodos().first().single().pastCount)
    }

    @Test fun changingCountPeriodToDailyDoesNotHideNewOccurrencesUntilTheOldPeriodEnds() = runBlocking {
        val original = seed()
        val rule = RecurrenceRuleJson.encode(RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 3))
        db.todoDao().upsert(original.copy(recurrenceType = rule.typeCode, repeatParamsJson = rule.paramsJson))
        val group = repository.observeIncompleteTodos().first().single()
        assertTrue(group.source.throughDate > today)
        repository.dismissIncompleteTodos(group.source).getOrThrow()
        repository.saveTodo(original.id, original.title, original.description, null, LocalDate.parse(original.startDate),
            null, RecurrenceRule.daily(), null).getOrThrow()
        val newGroup = repository.observeIncompleteTodos().first().single()
        assertEquals(1, newGroup.count)
        assertEquals(0, newGroup.pastCount)
        assertEquals(today, newGroup.source.throughDate)
    }

    @Test fun archivedSourceCannotBeReRegisteredAndPastRowsAreNotDeleted() = runBlocking {
        val original = seed()
        val source = repository.observeIncompleteTodos().first().single().source
        db.todoDao().upsert(original.copy(archivedAt = 100))
        assertTrue(repository.observeIncompleteTodos().first().isEmpty())
        assertTrue(repository.registerIncompleteTodo(source, registration()).isFailure)
        assertEquals(3, db.todoExecutionDao().findForTodo(original.id).size)
        assertEquals(1, db.todoDao().backupCount())
    }
}

private class InboxSettings : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val dayEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) { notificationPermissionRequested.value = value }
}

private class InboxScheduler : NotificationScheduler {
    override val notificationCount = MutableStateFlow(0)
    override fun systemState() = NotificationSystemState(true, false, true, false, true)
    override suspend fun reconcileTodo(todoId: String) = Unit
    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) = Unit
}
