package com.mochisofts.mata.app

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.EntitlementRepository
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.data.holiday.HolidayWorkScheduler
import com.mochisofts.mata.data.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@HiltViewModel
class MataAppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val historyReconciler: HistoryReconciler,
    private val holidayRepository: HolidayRepository,
    private val holidayWorkScheduler: HolidayWorkScheduler,
    private val widgetUpdater: WidgetUpdater,
    private val entitlementRepository: EntitlementRepository,
    private val adsConsentRepository: AdsConsentRepository,
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    val theme: StateFlow<AppTheme> = settingsRepository.theme
        .catch { emit(AppTheme.SYSTEM) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppTheme.SYSTEM,
        )

    internal suspend fun resolveExternalNavigation(
        request: ExternalNavigation,
    ): ExternalNavigationResolution = when (request) {
        ExternalNavigation.Fallback -> ExternalNavigationResolution(TodoListRoute())

        is ExternalNavigation.Notification -> {
            val todo = todoRepository.getTodo(request.todoId)
            if (todo == null || todo.archivedAt != null) {
                ExternalNavigationResolution(TodoListRoute(showTodoNotFound = true))
            } else {
                runCatching { notificationScheduler.reconcileTodo(request.todoId) }
                ExternalNavigationResolution(
                    TodoListRoute(selectedDate = request.logicalDate.toString()),
                )
            }
        }

        is ExternalNavigation.Widget -> {
            val targetTodoMissing = request.todoId?.let { todoId ->
                val todo = todoRepository.getTodo(todoId)
                todo == null || todo.archivedAt != null
            } ?: false
            if (targetTodoMissing) {
                ExternalNavigationResolution(TodoListRoute(showTodoNotFound = true))
            } else {
                val categoryKey = if (request.mode == MainActivity.WIDGET_MODE_CATEGORY) {
                    resolveCategoryKey(request.categoryKey)
                } else {
                    null
                }
                ExternalNavigationResolution(
                    TodoListRoute(
                        selectedDate = request.selectedDate.toString(),
                        initialMode = request.mode,
                        selectedCategoryKey = categoryKey,
                    ),
                )
            }
        }
    }

    private suspend fun resolveCategoryKey(requestedKey: String?): String {
        if (requestedKey == MainActivity.WIDGET_UNCATEGORIZED_KEY) {
            return requestedKey
        }
        val requestedCategory = requestedKey?.let { categoryId ->
            categoryRepository.getCategory(categoryId)
        }
        if (requestedCategory != null) {
            return requestedCategory.id
        }
        return categoryRepository.observeCategories().first()
            .firstOrNull()
            ?.id
            ?: MainActivity.WIDGET_UNCATEGORIZED_KEY
    }

    fun firstContentRendered(activity: Activity) {
        adsConsentRepository.gatherConsent(activity)
    }

    fun appResumed() {
        viewModelScope.launch {
            widgetUpdater.ensureScheduledIfWidgetsExist()
            if (runCatching { holidayRepository.needsRefresh() }.getOrDefault(false)) {
                holidayWorkScheduler.enqueueImmediate()
            }
            runCatching {
                do {
                    val result = historyReconciler.reconcile()
                    if (result.hasMore) yield()
                } while (result.hasMore)
            }
            runCatching { notificationScheduler.reconcileAll() }
            runCatching { entitlementRepository.refresh() }
        }
    }
}
