package com.mochisofts.mata.app

import com.mochisofts.mata.core.navigation.CategoryTodoListRoute
import com.mochisofts.mata.core.navigation.TodoListRoute
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalNavigationTest {
    @Test
    fun widgetRoutesDateAndCategoryToTheirIndependentScreens() {
        val date = LocalDate.of(2026, 8, 24)

        assertEquals(
            TodoListRoute(selectedDate = date.toString()),
            resolvedWidgetRoute(
                ExternalNavigation.Widget(
                    selectedDate = date,
                    mode = MainActivity.WIDGET_MODE_DATE,
                    categoryKey = null,
                    todoId = null,
                ),
                selectedCategoryKey = null,
            ),
        )
        assertEquals(
            CategoryTodoListRoute(selectedCategoryKey = CATEGORY_ID),
            resolvedWidgetRoute(
                ExternalNavigation.Widget(
                    selectedDate = date,
                    mode = MainActivity.WIDGET_MODE_CATEGORY,
                    categoryKey = CATEGORY_ID,
                    todoId = null,
                ),
                selectedCategoryKey = CATEGORY_ID,
            ),
        )
    }

    @Test
    fun validNotification_isParsedWithTypedDate() {
        val request = parseExternalNavigation(
            action = MainActivity.ACTION_OPEN_NOTIFICATION,
            todoId = TODO_ID,
            logicalDate = "2026-08-24",
            candidateKey = "$TODO_ID|notification|2026-08-24|1",
        )

        assertEquals(
            ExternalNavigation.Notification(
                todoId = TODO_ID,
                logicalDate = LocalDate.of(2026, 8, 24),
                candidateKey = "$TODO_ID|notification|2026-08-24|1",
            ),
            request,
        )
    }

    @Test
    fun malformedNotification_fallsBackToNormalLaunch() {
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_NOTIFICATION,
                todoId = "not-a-uuid",
                logicalDate = "2026-08-24",
                candidateKey = "candidate",
            ),
        )
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_NOTIFICATION,
                todoId = TODO_ID,
                logicalDate = "2026-02-30",
                candidateKey = "candidate",
            ),
        )
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_NOTIFICATION,
                todoId = TODO_ID,
                logicalDate = "2026-08-24",
                candidateKey = "candidate\nkey",
            ),
        )
    }

    @Test
    fun validWidgetCategory_isParsed() {
        val request = parseExternalNavigation(
            action = MainActivity.ACTION_OPEN_WIDGET,
            widgetDate = "2026-08-24",
            widgetMode = MainActivity.WIDGET_MODE_CATEGORY,
            widgetCategoryKey = CATEGORY_ID,
        )

        assertEquals(
            ExternalNavigation.Widget(
                selectedDate = LocalDate.of(2026, 8, 24),
                mode = MainActivity.WIDGET_MODE_CATEGORY,
                categoryKey = CATEGORY_ID,
                todoId = null,
            ),
            request,
        )
    }

    @Test
    fun invalidWidgetArguments_fallBackToNormalLaunch() {
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_WIDGET,
                widgetDate = "2026-08-24",
                widgetMode = "UNKNOWN",
            ),
        )
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_WIDGET,
                widgetDate = "0000-01-01",
                widgetMode = MainActivity.WIDGET_MODE_DATE,
            ),
        )
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_WIDGET,
                widgetDate = "2026-08-24",
                widgetMode = MainActivity.WIDGET_MODE_CATEGORY,
                widgetCategoryKey = "deleted-or-invalid",
            ),
        )
        assertEquals(
            ExternalNavigation.Fallback,
            parseExternalNavigation(
                action = MainActivity.ACTION_OPEN_WIDGET,
                todoId = "not-a-uuid",
                widgetDate = "2026-08-24",
                widgetMode = MainActivity.WIDGET_MODE_DATE,
            ),
        )
    }

    @Test
    fun recreatedActivity_doesNotReprocessHandledIntent() {
        val request = ExternalNavigation.Widget(
            selectedDate = LocalDate.of(2026, 8, 24),
            mode = MainActivity.WIDGET_MODE_DATE,
            categoryKey = null,
            todoId = TODO_ID,
        )

        assertNull(
            resolveExternalNavigationOnCreate(
                restoringActivity = true,
                restoredPending = null,
                intentRequest = request,
            ),
        )
        assertEquals(
            request,
            resolveExternalNavigationOnCreate(
                restoringActivity = true,
                restoredPending = request,
                intentRequest = request,
            ),
        )
    }

    private companion object {
        const val TODO_ID = "11111111-1111-4111-8111-111111111111"
        const val CATEGORY_ID = "22222222-2222-4222-8222-222222222222"
    }
}
