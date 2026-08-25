package com.mochisofts.mata.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mochisofts.mata.R
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LibrariesLoadState {
    data object Loading : LibrariesLoadState
    data class Loaded(val libraries: List<Library>) : LibrariesLoadState
    data object Error : LibrariesLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val resources = LocalResources.current
    var query by rememberSaveable { mutableStateOf("") }
    var selectedLibraryId by rememberSaveable { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var loadState by remember(resources) {
        mutableStateOf<LibrariesLoadState>(LibrariesLoadState.Loading)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(resources, retryKey) {
        loadState = LibrariesLoadState.Loading
        loadState = withContext(Dispatchers.Default) {
            runCatching {
                val json = resources.openRawResource(R.raw.aboutlibraries)
                    .bufferedReader()
                    .use { it.readText() }
                Libs.Builder()
                    .withJson(json)
                    .build()
                    .libraries
                    .sortedBy { it.name.lowercase(Locale.ROOT) }
            }.fold(
                onSuccess = LibrariesLoadState::Loaded,
                onFailure = { LibrariesLoadState.Error },
            )
        }
    }

    val loadedLibraries = (loadState as? LibrariesLoadState.Loaded)?.libraries.orEmpty()
    val selectedLibrary = loadedLibraries.firstOrNull { it.uniqueId == selectedLibraryId }
    BackHandler(enabled = selectedLibrary != null) { selectedLibraryId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.licenses_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = stringResource(R.string.licenses_clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            when (val state = loadState) {
                LibrariesLoadState.Loading -> LicenseMessage(
                    message = stringResource(R.string.licenses_loading),
                    showProgress = true,
                )
                LibrariesLoadState.Error -> LicenseMessage(
                    message = stringResource(R.string.licenses_load_error),
                    action = stringResource(R.string.action_retry),
                    onAction = { retryKey += 1 },
                )
                is LibrariesLoadState.Loaded -> {
                    val filteredLibraries = remember(state.libraries, query) {
                        state.libraries.filter { matchesLibraryName(it.name, query) }
                    }
                    if (filteredLibraries.isEmpty()) {
                        LicenseMessage(
                            message = stringResource(
                                if (query.isBlank()) {
                                    R.string.licenses_empty
                                } else {
                                    R.string.licenses_search_empty
                                },
                            ),
                        )
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(filteredLibraries, key = Library::uniqueId) { library ->
                                LicenseLibraryRow(
                                    library = library,
                                    onClick = { selectedLibraryId = library.uniqueId },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    selectedLibrary?.let { library ->
        LicenseDetailDialog(
            library = library,
            onDismiss = { selectedLibraryId = null },
        )
    }
}

@Composable
private fun LicenseLibraryRow(library: Library, onClick: () -> Unit) {
    val licenseNames = library.licenses.joinToString { it.name }
        .ifBlank { stringResource(R.string.licenses_unknown) }
    val supportingText = listOfNotNull(
        library.artifactVersion?.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.licenses_version_format, it)
        },
        licenseNames,
    ).joinToString(" ・ ")
    ListItem(
        headlineContent = { Text(library.name) },
        supportingContent = { Text(supportingText) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun LicenseDetailDialog(library: Library, onDismiss: () -> Unit) {
    val body = licenseBodyOrNull(library.licenses.map { it.licenseContent })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.licenses_detail_title, library.name)) },
        text = {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = body ?: stringResource(R.string.licenses_body_load_error),
                    color = if (body == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun LicenseMessage(
    message: String,
    showProgress: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showProgress) CircularProgressIndicator()
            Text(message)
            if (action != null) {
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

internal fun matchesLibraryName(name: String, query: String): Boolean {
    val normalizedQuery = query.trim()
    return normalizedQuery.isEmpty() || name.contains(normalizedQuery, ignoreCase = true)
}

internal fun licenseBodyOrNull(contents: Iterable<String?>): String? = contents
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .distinct()
    .joinToString("\n\n")
    .takeIf(String::isNotEmpty)
