package com.mochisofts.mata.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCoreConfigurationSpecCoverageTest {
    @Test
    fun app013_gradleDefinesTheReleaseAndDebugSdkIdentityContract() {
        val build = source("build.gradle")

        assertTrue(Regex("""namespace\s+['\"]com\.mochisofts\.mata['\"]""").containsMatchIn(build))
        assertTrue(Regex("""applicationId\s+['\"]com\.mochisofts\.mata['\"]""").containsMatchIn(build))
        assertTrue(Regex("""applicationIdSuffix\s+['\"]\.debug['\"]""").containsMatchIn(build))
        assertTrue(Regex("""\bminSdk\s+26\b""").containsMatchIn(build))
        assertTrue(Regex("""\btargetSdk\s+36\b""").containsMatchIn(build))
        assertTrue(
            Regex(
                """compileSdk\s*\{\s*version\s*=\s*release\(37\)\s*\{\s*minorApiLevel\s*=\s*1""",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(build),
        )

        assertEquals("MATA", stringValue("src/main/res/values/strings.xml", "app_name"))
        assertEquals("MATA Dev", stringValue("src/debug/res/values/strings.xml", "app_name"))
    }

    @Test
    fun dat002_externalTransmissionCodeCannotReachUserEnteredTodoData() {
        val productionRoot = locateDirectory("src/main/java")
        val kotlinSources = productionRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associateWith(File::readText)
        val firstPartyNetworkClients = kotlinSources
            .filterValues { source ->
                "java.net.HttpURLConnection" in source || "java.net.URL" in source
            }
            .keys
            .map { it.relativeTo(productionRoot).invariantSeparatorsPath }
            .toSet()
        assertEquals(
            setOf("com/mochisofts/mata/data/holiday/HolidaysJpApi.kt"),
            firstPartyNetworkClients,
        )

        val transmissionSources = kotlinSources
            .filterKeys { file ->
                val path = file.invariantSeparatorsPath
                "/data/ads/" in path || "/data/holiday/" in path || "/ui/ads/" in path
            }
            .values
            .joinToString("\n")
        assertFalse(
            Regex("""import com\.mochisofts\.mata\.(?:data\.local|domain\.model)\.(?:Todo|Category|Archived)""")
                .containsMatchIn(transmissionSources),
        )
        assertFalse(
            Regex("""\b(?:todo|category)\.(?:title|description|name)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(transmissionSources),
        )
        listOf(
            "addKeyword",
            "setContentUrl",
            "setPublisherProvidedId",
            "customTargeting",
            "networkExtras",
            "neighboringContentUrls",
        ).forEach { targetingApi -> assertFalse(transmissionSources.contains(targetingApi)) }
        assertTrue(transmissionSources.contains("hasBody = false"))

        val build = source("build.gradle")
        listOf("firebase", "retrofit", "okhttp", "supabase", "appwrite")
            .forEach { cloudDependency ->
                assertFalse(build.contains(cloudDependency, ignoreCase = true))
            }
    }

    @Test
    fun dat004_onlySafManualBackupCanAccessUserSelectedStorage() {
        val manifest = source("src/main/AndroidManifest.xml")
        setOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        ).forEach { broadPermission -> assertFalse(manifest.contains(broadPermission)) }

        val settingsScreen = source(
            "src/main/java/com/mochisofts/mata/ui/settings/SettingsScreen.kt",
        )
        assertTrue(settingsScreen.contains("ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)"))
        assertTrue(settingsScreen.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(settingsScreen.contains("viewModel::createTargetSelected"))
        assertTrue(settingsScreen.contains("viewModel::restoreFileSelected"))
    }

    @Test
    fun sta002_onlyCalendarHistoryExposesUndoAfterCompletion() {
        val todoList = source(
            "src/main/java/com/mochisofts/mata/ui/todolist/TodoListScreen.kt",
        )
        val notification = source(
            "src/main/java/com/mochisofts/mata/app/notification/NotificationReceivers.kt",
        )
        val widgetAction = source(
            "src/main/java/com/mochisofts/mata/widget/WidgetTodoActionActivity.kt",
        )
        val widgetInfrastructure = source(
            "src/main/java/com/mochisofts/mata/data/widget/WidgetUpdateInfrastructure.kt",
        )
        val calendarHistory = source(
            "src/main/java/com/mochisofts/mata/ui/calendar/CalendarHistoryScreen.kt",
        )

        assertFalse(todoList.contains("R.string.action_undo"))
        assertFalse(notification.contains("ACTION_UNDO"))
        assertFalse(notification.contains("R.string.action_undo"))
        assertTrue(
            Regex(
                """\.onSuccess\s*\{[^}]*presenter\.cancel\(""",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(notification),
        )
        assertFalse(widgetAction.contains("recordCompletionUndo"))
        assertFalse(widgetAction.contains("undoOperationId"))
        assertFalse(widgetInfrastructure.contains("WidgetUndoExpiryWorker"))
        assertTrue(calendarHistory.contains("R.string.action_undo"))
    }

    private fun stringValue(relativePath: String, name: String): String {
        val source = source(relativePath)
        val match = Regex("""<string\s+name=[\"]${Regex.escape(name)}[\"]>([^<]+)</string>""")
            .find(source)
        assertNotNull("Missing string resource $name in $relativePath", match)
        return requireNotNull(match).groupValues[1]
    }

    private fun source(relativePath: String): String = locate(relativePath).readText()

    private fun locate(relativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        )
        val located = candidates.firstOrNull(File::isFile)
        assertNotNull("Could not locate $relativePath from $workingDirectory", located)
        return requireNotNull(located)
    }

    private fun locateDirectory(relativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        )
        val located = candidates.firstOrNull(File::isDirectory)
        assertNotNull("Could not locate $relativePath from $workingDirectory", located)
        return requireNotNull(located)
    }
}
