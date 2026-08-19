package com.mochisofts.mata.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.navigation.CategoryEditorRoute
import com.mochisofts.mata.core.navigation.CategoryListRoute
import com.mochisofts.mata.core.navigation.CalendarHistoryRoute
import com.mochisofts.mata.core.navigation.ArchivedTodoDetailRoute
import com.mochisofts.mata.core.navigation.ArchivedTodoListRoute
import com.mochisofts.mata.core.navigation.PlaceholderRoute
import com.mochisofts.mata.core.navigation.SettingsRoute
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.ui.category.CategoryEditorScreen
import com.mochisofts.mata.ui.category.CategoryListScreen
import com.mochisofts.mata.ui.calendar.CalendarHistoryScreen
import com.mochisofts.mata.ui.archive.ARCHIVE_RESULT_KEY
import com.mochisofts.mata.ui.archive.ArchiveDetailScreen
import com.mochisofts.mata.ui.archive.ArchiveListScreen
import com.mochisofts.mata.ui.settings.SettingsScreen
import com.mochisofts.mata.ui.todoeditor.TodoEditorScreen
import com.mochisofts.mata.ui.todolist.TodoListScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MataAppViewModel by viewModels()
    private var externalNavigation by mutableStateOf<ExternalNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalNavigation = intent.toExternalNavigation()
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            MataTheme(appTheme = theme) {
                LaunchedEffect(viewModel) {
                    withFrameNanos { }
                    viewModel.firstContentRendered(this@MainActivity)
                }
                MataApp(
                    externalNavigation = externalNavigation,
                    onExternalNavigationHandled = { externalNavigation = null },
                    onNotificationOpened = viewModel::notificationOpened,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalNavigation = intent.toExternalNavigation()
    }

    override fun onResume() {
        super.onResume()
        viewModel.appResumed()
    }

    companion object {
        const val ACTION_OPEN_NOTIFICATION = "com.mochisofts.mata.action.OPEN_NOTIFICATION"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_LOGICAL_DATE = "logical_date"
        const val EXTRA_CANDIDATE_KEY = "candidate_key"
        const val ACTION_OPEN_WIDGET = "com.mochisofts.mata.action.OPEN_WIDGET"
        const val EXTRA_WIDGET_DATE = "widget_date"
        const val EXTRA_WIDGET_MODE = "widget_mode"
        const val EXTRA_WIDGET_CATEGORY_KEY = "widget_category_key"
        const val WIDGET_MODE_DATE = "DATE"
        const val WIDGET_MODE_CATEGORY = "CATEGORY"
        const val WIDGET_UNCATEGORIZED_KEY = "__uncategorized__"
    }
}

@Composable
private fun MataApp(
    externalNavigation: ExternalNavigation?,
    onExternalNavigationHandled: () -> Unit,
    onNotificationOpened: (String) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val navigateToDestination: (MataDestination) -> Unit = { destination ->
        when (destination) {
            MataDestination.TODOS -> navController.navigate(TodoListRoute()) { launchSingleTop = true }
            MataDestination.CALENDAR -> navController.navigate(CalendarHistoryRoute) { launchSingleTop = true }
            MataDestination.CATEGORIES -> navController.navigate(CategoryListRoute) { launchSingleTop = true }
            MataDestination.ARCHIVE -> navController.navigate(ArchivedTodoListRoute) { launchSingleTop = true }
            MataDestination.SETTINGS -> navController.navigate(SettingsRoute) { launchSingleTop = true }
        }
    }

    LaunchedEffect(externalNavigation) {
        externalNavigation?.let { request ->
            val route = when (request) {
                is ExternalNavigation.Notification -> {
                    onNotificationOpened(request.todoId)
                    TodoListRoute(selectedDate = request.logicalDate)
                }
                is ExternalNavigation.Widget -> TodoListRoute(
                    selectedDate = request.selectedDate,
                    initialMode = request.mode,
                    selectedCategoryKey = request.categoryKey,
                )
            }
            navController.navigate(route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
            onExternalNavigationHandled()
        }
    }

    NavHost(navController = navController, startDestination = TodoListRoute()) {
        composable<TodoListRoute> {
            TodoListScreen(
                onAddTodo = { navController.navigate(TodoEditorRoute()) },
                onEditTodo = { navController.navigate(TodoEditorRoute(it)) },
                onDestination = navigateToDestination,
            )
        }
        composable<TodoEditorRoute> {
            MataContentFrame(maxWidth = 720.dp) {
                TodoEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                )
            }
        }
        composable<CalendarHistoryRoute> {
            CalendarHistoryScreen(onDestination = navigateToDestination)
        }
        composable<ArchivedTodoListRoute> {
            ArchiveListScreen(
                onOpenDetail = { navController.navigate(ArchivedTodoDetailRoute(it)) },
                onDestination = navigateToDestination,
            )
        }
        composable<ArchivedTodoDetailRoute> {
            MataContentFrame(maxWidth = 840.dp) {
                ArchiveDetailScreen(
                    onBack = navController::popBackStack,
                    onFinished = { messageRes ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(ARCHIVE_RESULT_KEY, messageRes)
                        navController.popBackStack()
                    },
                    onNotFound = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(ARCHIVE_RESULT_KEY, R.string.error_todo_not_found)
                        navController.popBackStack()
                    },
                )
            }
        }
        composable<CategoryListRoute> {
            CategoryListScreen(
                onAdd = { navController.navigate(CategoryEditorRoute()) },
                onEdit = { navController.navigate(CategoryEditorRoute(it)) },
                onDestination = navigateToDestination,
            )
        }
        composable<CategoryEditorRoute> {
            MataContentFrame(maxWidth = 720.dp) {
                CategoryEditorScreen(
                    onBack = navController::popBackStack,
                    onSaved = { navController.popBackStack() },
                )
            }
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onDestination = navigateToDestination,
                onRestoreCompleted = {
                    navController.navigate(TodoListRoute(initialMode = MainActivity.WIDGET_MODE_DATE)) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<PlaceholderRoute> { entry ->
            val route = entry.toRoute<PlaceholderRoute>()
            val destination = runCatching { MataDestination.valueOf(route.destination) }
                .getOrDefault(MataDestination.TODOS)
            PlaceholderScreen(destination, navigateToDestination)
        }
    }
}

private sealed interface ExternalNavigation {
    data class Notification(val todoId: String, val logicalDate: String) : ExternalNavigation
    data class Widget(
        val selectedDate: String,
        val mode: String,
        val categoryKey: String?,
    ) : ExternalNavigation
}

private fun Intent.toExternalNavigation(): ExternalNavigation? = when (action) {
    MainActivity.ACTION_OPEN_NOTIFICATION -> {
        val todoId = getStringExtra(MainActivity.EXTRA_TODO_ID) ?: return null
        val logicalDate = getStringExtra(MainActivity.EXTRA_LOGICAL_DATE) ?: return null
        ExternalNavigation.Notification(todoId, logicalDate)
    }
    MainActivity.ACTION_OPEN_WIDGET -> {
        val selectedDate = getStringExtra(MainActivity.EXTRA_WIDGET_DATE) ?: return null
        val mode = getStringExtra(MainActivity.EXTRA_WIDGET_MODE) ?: MainActivity.WIDGET_MODE_DATE
        val categoryKey = getStringExtra(MainActivity.EXTRA_WIDGET_CATEGORY_KEY)
        ExternalNavigation.Widget(selectedDate, mode, categoryKey)
    }
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    destination: MataDestination,
    onDestination: (MataDestination) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    MataAdaptiveNavigation(
        selected = destination,
        drawerState = drawerState,
        onSelect = onDestination,
    ) { layoutInfo ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(destination.labelRes)) },
                    navigationIcon = {
                        if (layoutInfo.navigationType == MataNavigationType.MODAL_DRAWER) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.content_description_open_menu),
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.placeholder_screen_message),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
