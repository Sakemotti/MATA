package com.mochisofts.mata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.navigation.CategoryEditorRoute
import com.mochisofts.mata.core.navigation.CategoryListRoute
import com.mochisofts.mata.core.navigation.PlaceholderRoute
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.ui.category.CategoryEditorScreen
import com.mochisofts.mata.ui.category.CategoryListScreen
import com.mochisofts.mata.ui.todoeditor.TodoEditorScreen
import com.mochisofts.mata.ui.todolist.TodoListScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MataTheme {
                MataApp()
            }
        }
    }
}

@Composable
private fun MataApp(navController: NavHostController = rememberNavController()) {
    val navigateToDestination: (MataDestination) -> Unit = { destination ->
        when (destination) {
            MataDestination.TODOS -> navController.navigate(TodoListRoute) { launchSingleTop = true }
            MataDestination.CATEGORIES -> navController.navigate(CategoryListRoute) { launchSingleTop = true }
            else -> navController.navigate(PlaceholderRoute(destination.name)) { launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = TodoListRoute) {
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
        composable<PlaceholderRoute> { entry ->
            val route = entry.toRoute<PlaceholderRoute>()
            val destination = runCatching { MataDestination.valueOf(route.destination) }
                .getOrDefault(MataDestination.TODOS)
            PlaceholderScreen(destination, navigateToDestination)
        }
    }
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
                scope.launch { drawerState.close() }
                if (selected != destination) onDestination(selected)
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
