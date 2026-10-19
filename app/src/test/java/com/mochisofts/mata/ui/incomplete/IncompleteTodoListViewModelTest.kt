package com.mochisofts.mata.ui.incomplete

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.IncompleteTodoGroup
import com.mochisofts.mata.domain.model.IncompleteTodoSource
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HistoryReconciliationResult
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncompleteTodoListViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private val source = IncompleteTodoSource("original", LocalDate.of(2026, 10, 17))

    @Test fun reconciliationAndBusyStatePreventDuplicateDismissals() = runTest {
        val repository = InboxTestRepository()
        val reconciler = InboxTestReconciler().apply { gate = CompletableDeferred() }
        val vm = IncompleteTodoListViewModel(SavedStateHandle(), repository, reconciler, InboxTestAds())
        runCurrent()
        assertTrue(vm.uiState.value.isReconciling)
        vm.dismiss(source)
        runCurrent()
        assertEquals(0, repository.dismissCalls)
        reconciler.gate!!.complete(Unit)
        runCurrent()
        repository.dismissGate = CompletableDeferred()
        vm.dismiss(source)
        vm.dismiss(source)
        runCurrent()
        assertEquals(1, repository.dismissCalls)
        assertEquals(source.todoId, vm.uiState.value.busyTodoId)
        repository.dismissGate!!.complete(Unit)
        runCurrent()
        assertNull(vm.uiState.value.busyTodoId)
        assertEquals(R.string.incomplete_hidden, vm.messages.first())
    }

    @Test fun reconciliationFailureSurvivesDataRefreshAndCanBeRetried() = runTest {
        val repository = InboxTestRepository()
        val reconciler = InboxTestReconciler().apply { fail = true }
        val vm = IncompleteTodoListViewModel(SavedStateHandle(), repository, reconciler, InboxTestAds())
        runCurrent()
        assertTrue(vm.uiState.value.hasHistoryError)
        vm.refresh(reconcile = false)
        runCurrent()
        assertTrue(vm.uiState.value.hasHistoryError)
        vm.dismiss(source)
        runCurrent()
        assertEquals(0, repository.dismissCalls)
        reconciler.fail = false
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.hasHistoryError)
        assertFalse(vm.uiState.value.isReconciling)
    }

    @Test fun loadFailureShowsErrorRatherThanSuccessfulEmptyAndRetryRecovers() = runTest {
        val repository = InboxTestRepository().apply { failLoad = true }
        val vm = IncompleteTodoListViewModel(SavedStateHandle(), repository, InboxTestReconciler(), InboxTestAds())
        runCurrent()
        assertTrue(vm.uiState.value.hasLoadError)
        assertFalse(vm.uiState.value.isLoading)
        repository.failLoad = false
        vm.refresh(reconcile = false)
        runCurrent()
        assertFalse(vm.uiState.value.hasLoadError)
        assertFalse(vm.uiState.value.isLoading)
    }
}

private class InboxTestReconciler : HistoryReconciler {
    var gate: CompletableDeferred<Unit>? = null
    var fail = false
    override suspend fun reconcile(maxRecords: Int): HistoryReconciliationResult {
        gate?.await()
        check(!fail)
        return HistoryReconciliationResult(0, false)
    }
}

private class InboxTestAds : AdsConsentRepository {
    override val state = MutableStateFlow(AdsRuntimeState())
    override val events: Flow<AdsConsentEvent> = emptyFlow()
    override fun gatherConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}

private class InboxTestRepository : TodoRepository {
    var failLoad = false
    var dismissCalls = 0
    var dismissGate: CompletableDeferred<Unit>? = null
    override fun observeIncompleteTodos(): Flow<List<IncompleteTodoGroup>> =
        if (failLoad) flow { error("injected") } else flowOf(emptyList())
    override suspend fun dismissIncompleteTodos(source: IncompleteTodoSource): Result<Unit> {
        dismissCalls++
        dismissGate?.await()
        return Result.success(Unit)
    }
    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> = flowOf(emptyList())
    override fun observeTodos(): Flow<List<Todo>> = flowOf(emptyList())
    override suspend fun getTodo(id: String): Todo? = null
    override suspend fun saveTodo(
        id: String?, title: String, description: String, categoryId: String?, startDate: LocalDate,
        endDate: LocalDate?, recurrenceRule: RecurrenceRule, dueMinutes: Int?,
        notifications: List<TodoNotification>, dueDate: LocalDate?, carryOverEnabled: Boolean,
    ) = Result.success("new")
    override suspend fun setCompleted(todoId: String, logicalDate: LocalDate, completed: Boolean,
        operationId: String, scheduledLogicalDate: LocalDate) = Result.success(Unit)
    override suspend fun setSkipped(todoId: String, logicalDate: LocalDate, skipped: Boolean,
        operationId: String, scheduledLogicalDate: LocalDate) = Result.success(Unit)
    override suspend fun archiveTodo(id: String) = Result.success(Unit)
    override suspend fun restoreTodo(id: String) = Result.success(Unit)
    override suspend fun deleteTodo(id: String) = Result.success(Unit)
}
