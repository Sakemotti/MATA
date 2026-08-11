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
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.mochisofts.mata.core.navigation.PlaceholderRoute
import com.mochisofts.mata.core.navigation.SettingsRoute
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.ui.category.CategoryEditorScreen
import com.mochisofts.mata.ui.category.CategoryListScreen
import com.mochisofts.mata.ui.settings.SettingsScreen
import com.mochisofts.mata.ui.todoeditor.TodoEditorScreen
import com.mochisofts.mata.ui.todolist.TodoListScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MataAppViewModel by viewModels()
    private var notificationNavigation by mutableStateOf<NotificationNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationNavigation = intent.toNotificationNavigation()
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            MataTheme(appTheme = theme) {
                MataApp(
                    notificationNavigation = notificationNavigation,
                    onNotificationHandled = { notificationNavigation = null },
                    onNotificationOpened = viewModel::notificationOpened,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationNavigation = intent.toNotificationNavigation()
    }

    companion object {
        const val ACTION_OPEN_NOTIFICATION = "com.mochisofts.mata.action.OPEN_NOTIFICATION"
        const val EXTRA_TODO_ID = "todo_id"
        const val EXTRA_LOGICAL_DATE = "logical_date"
        const val EXTRA_CANDIDATE_KEY = "candidate_key"
    }
}

@Composable
private fun MataApp(
    notificationNavigation: NotificationNavigation?,
    onNotificationHandled: () -> Unit,
    onNotificationOpened: (String) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val navigateToDestination: (MataDestination) -> Unit = { destination ->
        when (destination) {
            MataDestination.TODOS -> navController.navigate(TodoListRoute()) { launchSingleTop = true }
            MataDestination.CATEGORIES -> navController.navigate(CategoryListRoute) { launchSingleTop = true }
            MataDestination.SETTINGS -> navController.navigate(SettingsRoute) { launchSingleTop = true }
            else -> navController.navigate(PlaceholderRoute(destination.name)) { launchSingleTop = true }
        }
    }

    LaunchedEffect(notificationNavigation) {
        notificationNavigation?.let { request ->
            navController.navigate(TodoListRoute(request.logicalDate)) { launchSingleTop = true }
            onNotificationOpened(request.todoId)
            onNotificationHandled()
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
            TodoEditorScreen(
                onBack = navController::popBackStack,
                onSaved = { navController.popBackStack() },
            )
        }
        composable<CategoryListRoute> {
            CategoryListScreen(
                onAdd = { navController.navigate(CategoryEditorRoute()) },
                onEdit = { navController.navigate(CategoryEditorRoute(it)) },
                onDestination = navigateToDestination,
            )
        }
        composable<CategoryEditorRoute> {
            CategoryEditorScreen(
                onBack = navController::popBackStack,
                onSaved = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onDestination = navigateToDestination)
        }
        composable<PlaceholderRoute> { entry ->
            val route = entry.toRoute<PlaceholderRoute>()
            val destination = runCatching { MataDestination.valueOf(route.destination) }
                .getOrDefault(MataDestination.TODOS)
            PlaceholderScreen(destination, navigateToDestination)
        }
    }
}

private data class NotificationNavigation(
    val todoId: String,
    val logicalDate: String,
)

private fun Intent.toNotificationNavigation(): NotificationNavigation? {
    if (action != MainActivity.ACTION_OPEN_NOTIFICATION) return null
    val todoId = getStringExtra(MainActivity.EXTRA_TODO_ID) ?: return null
    val logicalDate = getStringExtra(MainActivity.EXTRA_LOGICAL_DATE) ?: return null
    return NotificationNavigation(todoId, logicalDate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    destination: MataDestination,
    onDestination: (MataDestination) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MataNavigationDrawer(destination) { selected ->
                scope.launch {
                    drawerState.close()
                    if (selected != destination) onDestination(selected)
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(destination.labelRes)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.content_description_open_menu),
                            )
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
