package com.mochisofts.mata.app

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.mataUsesDarkTheme
import com.mochisofts.mata.core.navigation.CategoryEditorRoute
import com.mochisofts.mata.core.navigation.CategoryListRoute
import com.mochisofts.mata.core.navigation.CategoryTodoListRoute
import com.mochisofts.mata.core.navigation.CalendarHistoryRoute
import com.mochisofts.mata.core.navigation.ArchivedTodoDetailRoute
import com.mochisofts.mata.core.navigation.ArchivedTodoListRoute
import com.mochisofts.mata.core.navigation.OpenSourceLicensesRoute
import com.mochisofts.mata.core.navigation.SettingsRoute
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.ui.category.CategoryEditorScreen
import com.mochisofts.mata.ui.category.CategoryListScreen
import com.mochisofts.mata.ui.categorytodolist.CategoryTodoListScreen
import com.mochisofts.mata.ui.calendar.CalendarHistoryScreen
import com.mochisofts.mata.ui.archive.ARCHIVE_RESULT_KEY
import com.mochisofts.mata.ui.archive.ArchiveDetailScreen
import com.mochisofts.mata.ui.archive.ArchiveListScreen
import com.mochisofts.mata.ui.settings.SettingsScreen
import com.mochisofts.mata.ui.settings.OpenSourceLicensesScreen
import com.mochisofts.mata.ui.todoeditor.TodoEditorScreen
import com.mochisofts.mata.ui.todolist.TodoListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MataAppViewModel by viewModels()
    private var externalNavigation by mutableStateOf<ExternalNavigation?>(null)
    private val oneShotFullyDrawnReporter = OneShotFullyDrawnReporter(::reportFullyDrawn)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        externalNavigation = resolveExternalNavigationOnCreate(
            restoringActivity = savedInstanceState != null,
            restoredPending = savedInstanceState?.pendingExternalNavigation(),
            intentRequest = intent.toExternalNavigation(),
        )
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val startupState by viewModel.startupState.collectAsStateWithLifecycle()
            val foldingFeatures by rememberFoldingFeatures(this)
            val darkTheme = mataUsesDarkTheme(theme)
            SideEffect {
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle,
                )
            }
            CompositionLocalProvider(LocalMataFoldingFeatures provides foldingFeatures) {
                MataTheme(appTheme = theme) {
                    when (startupState) {
                        StartupState.Initializing -> StartupLoadingScreen()
                        StartupState.Failed -> StartupErrorScreen(
                            onRetry = viewModel::retryStartup,
                            onContentReady = {
                                markContentReady(initializeExternalServices = false)
                            },
                        )
                        is StartupState.Ready -> {
                            MataApp(
                                externalNavigation = externalNavigation,
                                onExternalNavigationHandled = { externalNavigation = null },
                                resolveExternalNavigation = viewModel::resolveExternalNavigation,
                                onContentReady = {
                                    markContentReady(initializeExternalServices = true)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalNavigation = intent.toExternalNavigation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putPendingExternalNavigation(externalNavigation)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        viewModel.appResumed()
    }

    private fun markContentReady(initializeExternalServices: Boolean) {
        if (!oneShotFullyDrawnReporter.report()) return
        if (initializeExternalServices) viewModel.firstContentRendered(this)
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
private fun rememberFoldingFeatures(activity: ComponentActivity): State<List<FoldingFeature>> {
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    return produceState(initialValue = emptyList(), activity, tracker) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tracker.windowLayoutInfo(activity).collect { layoutInfo ->
                value = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>()
            }
        }
    }
}

@Composable
private fun StartupLoadingScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.startup_loading_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StartupErrorScreen(
    onRetry: () -> Unit,
    onContentReady: () -> Unit,
) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        onContentReady()
    }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.startup_error_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.startup_error_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun MataApp(
    externalNavigation: ExternalNavigation?,
    onExternalNavigationHandled: () -> Unit,
    resolveExternalNavigation: suspend (ExternalNavigation) -> ExternalNavigationResolution,
    onContentReady: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val navigateToDestination: (MataDestination) -> Unit = { destination ->
        when (destination) {
            MataDestination.TODOS -> navController.navigate(TodoListRoute()) { launchSingleTop = true }
            MataDestination.CATEGORY_TODOS -> {
                navController.navigate(CategoryTodoListRoute) { launchSingleTop = true }
            }
            MataDestination.CALENDAR -> navController.navigate(CalendarHistoryRoute) { launchSingleTop = true }
            MataDestination.CATEGORIES -> navController.navigate(CategoryListRoute) { launchSingleTop = true }
            MataDestination.ARCHIVE -> navController.navigate(ArchivedTodoListRoute) { launchSingleTop = true }
            MataDestination.SETTINGS -> navController.navigate(SettingsRoute) { launchSingleTop = true }
        }
    }

    LaunchedEffect(externalNavigation) {
        externalNavigation?.let { request ->
            val route = runCatching { resolveExternalNavigation(request).route }
                .getOrDefault(TodoListRoute())
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
                contentReadinessEnabled = externalNavigation == null,
                onContentReady = onContentReady,
            )
        }
        composable<CategoryTodoListRoute> {
            CategoryTodoListScreen(
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
                onOpenSourceLicenses = {
                    navController.navigate(OpenSourceLicensesRoute) { launchSingleTop = true }
                },
                onRestoreCompleted = {
                    navController.navigate(TodoListRoute(initialMode = MainActivity.WIDGET_MODE_DATE)) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<OpenSourceLicensesRoute> {
            MataContentFrame(maxWidth = 840.dp) {
                OpenSourceLicensesScreen(onBack = navController::popBackStack)
            }
        }
    }
}
