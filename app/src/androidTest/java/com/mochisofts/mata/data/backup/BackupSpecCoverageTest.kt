package com.mochisofts.mata.data.backup

import android.content.Context
import androidx.room.withTransaction
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.HolidayEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.local.WidgetInstanceStateEntity
import com.mochisofts.mata.data.repository.DataStoreSettingsRepository
import com.mochisofts.mata.data.repository.HistorySnapshotJson
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HistoryReconciliationResult
import com.mochisofts.mata.domain.repository.NotificationScheduler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSpecCoverageTest {
    private lateinit var context: Context
    private lateinit var database: MataDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var dataStoreFile: File
    private lateinit var settingsRepository: DataStoreSettingsRepository
    private lateinit var writer: BackupArchiveWriter
    private val reader = BackupArchiveReader()
    private val clock = Clock.fixed(
        Instant.parse("2026-08-11T03:00:00Z"),
        ZoneId.of("Asia/Tokyo"),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStoreFile = File(context.cacheDir, "backup-spec-${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile },
        )
        settingsRepository = DataStoreSettingsRepository(dataStore, DataMutationGate())
        writer = BackupArchiveWriter(
            context = context,
            database = database,
            categoryDao = database.categoryDao(),
            todoDao = database.todoDao(),
            notificationDao = database.todoNotificationDao(),
            executionDao = database.todoExecutionDao(),
            periodResultDao = database.periodResultDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            settingsRepository = settingsRepository,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        dataStoreFile.delete()
        context.getSharedPreferences("backup_operation", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun st019_allUserDataStreamsToAValidatedBackup() = runBlocking {
        seedAllUserData()
        val progress = mutableListOf<Int>()

        val archive = ByteArrayOutputStream().also { output ->
            writer.write(output) { phase, value ->
                if (phase == BackupOperationPhase.WRITING && value != null) progress += value
            }
        }.toByteArray()
        val extracted = temporaryDataFile()
        val summary = reader.extractAndValidate(ByteArrayInputStream(archive), extracted)
        val sink = CountingBackupSink()
        val parsed = reader.parseValidatedData(extracted, summary.manifest, sink)

        assertEquals(BackupCounts(2, 1, 1, 1, 1, 1), summary.manifest.counts)
        assertEquals(summary.manifest.counts, parsed.counts)
        assertEquals(summary.manifest.counts, sink.counts())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> before <= after })
        assertTrue(summary.manifest.dataUncompressedBytes > 0)
        assertEquals(64, summary.manifest.dataSha256.length)
        assertTrue(extracted.delete())
    }

    @Test
    fun st020_externalCachesConsentAndTransientStateAreExcluded() = runBlocking {
        seedAllUserData()
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("ump_consent_state")] = "granted"
            preferences[stringPreferencesKey("todo_editor_draft")] = "temporary draft"
            preferences[stringPreferencesKey("selected_calendar_date")] = "2026-08-11"
        }
        database.scheduledNotificationDao().upsert(
            ScheduledNotificationEntity(
                candidateKey = "candidate",
                todoId = TODO_ID,
                notificationSettingId = NOTIFICATION_ID,
                logicalDate = "2026-08-11",
                definitionRevision = 1,
                triggerAt = 100,
                requestCode = 10_000,
                schedulingMode = "exact",
                state = "scheduled",
                failureCode = null,
                createdAt = 100,
                updatedAt = 100,
            ),
        )
        database.holidayDao().insertAll(
            listOf(
                HolidayEntity(
                    date = "2026-08-11",
                    year = 2026,
                    name = "山の日",
                    sourceId = "holidays-jp",
                    sourceDataHash = "cache-hash",
                    fetchedAt = 100,
                ),
            ),
        )
        database.widgetInstanceStateDao().upsert(
            WidgetInstanceStateEntity(
                appWidgetId = 42,
                snapshotVersion = 1,
                snapshotJson = "{\"temporary\":true}",
                lastSuccessAt = 100,
                loadState = "ready",
                errorCode = null,
                lastFailureAt = null,
                undoOperationId = "temporary-operation",
                undoTodoTitle = "temporary-title",
                undoExpiresAt = 200,
                nextRefreshAt = 300,
                updatedAt = 100,
            ),
        )

        val entries = zipEntries(writeArchive())
        val root = parseObject(entries.getValue(DATA_ENTRY))
        val settings = root.getValue("settings").jsonObject
        val serialized = entries.getValue(DATA_ENTRY).toString(Charsets.UTF_8)

        assertEquals(
            setOf(
                "formatVersion",
                "settings",
                "categories",
                "todos",
                "notifications",
                "executions",
                "periodResults",
                "runtimeStates",
            ),
            root.keys,
        )
        assertEquals(
            setOf("dayEndHour", "weekStartDay", "showCompletedTodos", "theme"),
            settings.keys,
        )
        listOf(
            "ump_consent_state",
            "todo_editor_draft",
            "selected_calendar_date",
            "scheduled_notifications",
            "holidays-jp",
            "temporary-operation",
        ).forEach { excluded -> assertFalse(excluded, serialized.contains(excluded)) }
    }

    @Test
    fun st023_formatHashStructureTypeRangeReferenceAndCompatibilityFailBeforeMutation() = runBlocking {
        seedAllUserData()
        val validArchive = writeArchive()
        val validEntries = zipEntries(validArchive)
        val validData = validEntries.getValue(DATA_ENTRY).toString(Charsets.UTF_8)
        val validManifest = validEntries.getValue(MANIFEST_ENTRY).toString(Charsets.UTF_8)
        val differentCategoryId = "00000000-0000-0000-0000-000000000099"
        val invalidArchives = listOf(
            "format" to replaceManifest(
                validArchive,
                validManifest.replace(BACKUP_FORMAT_ID, "invalid.backup.format"),
            ),
            "hash" to replaceManifest(
                validArchive,
                validManifest.replace(Regex("\"sha256\":\"[0-9a-f]{64}\""), "\"sha256\":\"${"0".repeat(64)}\""),
            ),
            "structure" to replaceData(
                validArchive,
                validData.replace("\"runtimeStates\":[", "\"unexpected\":true,\"runtimeStates\":["),
            ),
            "type" to replaceData(
                validArchive,
                validData.replace("\"showCompletedTodos\":true", "\"showCompletedTodos\":\"true\""),
            ),
            "range" to replaceData(
                validArchive,
                validData.replace("\"dayEndHour\":4", "\"dayEndHour\":24"),
            ),
            "reference" to replaceData(
                validArchive,
                validData.replace("\"categoryId\":\"$CATEGORY_ID\"", "\"categoryId\":\"$differentCategoryId\""),
            ),
            "compatibility" to replaceManifest(
                validArchive,
                validManifest
                    .replace("\"formatVersion\":$BACKUP_FORMAT_VERSION", "\"formatVersion\":${BACKUP_FORMAT_VERSION + 1}")
                    .replace(
                        "\"minimumReaderVersion\":$BACKUP_FORMAT_VERSION",
                        "\"minimumReaderVersion\":${BACKUP_FORMAT_VERSION + 1}",
                    ),
            ),
        )

        invalidArchives.forEach { (caseName, archive) ->
            val extracted = temporaryDataFile()
            val error = runCatching {
                reader.extractAndValidate(ByteArrayInputStream(archive), extracted)
            }.exceptionOrNull()
            assertTrue(caseName, error is BackupFormatException)
            assertFalse(caseName, extracted.exists())
            assertEquals(caseName, 2, database.categoryDao().backupCount())
            assertEquals(caseName, 1, database.todoDao().backupCount())
        }
    }

    @Test
    fun std02_fileNameAndPayloadUseTheSpecifiedZipContainer() = runBlocking {
        seedAllUserData()
        val operationStore = BackupOperationStore(context).also(BackupOperationStore::clear)
        val coordinator = BackupCoordinator(context, operationStore, clock)
        val archive = writeArchive()

        assertEquals(
            "MATA_backup_20260811_120000.mata-backup",
            coordinator.suggestedFileName(),
        )
        assertEquals('P'.code.toByte(), archive[0])
        assertEquals('K'.code.toByte(), archive[1])
        assertEquals(listOf(DATA_ENTRY, MANIFEST_ENTRY), zipEntries(archive).keys.toList())
    }

    @Test
    fun std03_archiveContainsRequiredMetadataSettingsOrderingAndHistory() = runBlocking {
        seedAllUserData()
        val entries = zipEntries(writeArchive())
        val dataBytes = entries.getValue(DATA_ENTRY)
        val root = parseObject(dataBytes)
        val manifest = parseObject(entries.getValue(MANIFEST_ENTRY))
        val settings = root.getValue("settings").jsonObject

        assertEquals(BACKUP_FORMAT_ID, manifest.getValue("formatId").jsonPrimitive.content)
        assertEquals(BACKUP_FORMAT_VERSION, manifest.getValue("formatVersion").jsonPrimitive.int)
        assertEquals(1_786_417_200_000L, manifest.getValue("createdAt").jsonPrimitive.long)
        assertTrue(manifest.getValue("appVersionName").jsonPrimitive.content.isNotBlank())
        assertTrue(manifest.getValue("appVersionCode").jsonPrimitive.long >= 1)
        assertEquals(MataDatabase.SCHEMA_VERSION, manifest.getValue("roomSchemaVersion").jsonPrimitive.int)
        val integrity = manifest.getValue("data").jsonObject
        assertEquals(sha256(dataBytes), integrity.getValue("sha256").jsonPrimitive.content)
        assertEquals(dataBytes.size.toLong(), integrity.getValue("uncompressedBytes").jsonPrimitive.long)

        assertEquals(4, settings.getValue("dayEndHour").jsonPrimitive.int)
        assertEquals("sunday", settings.getValue("weekStartDay").jsonPrimitive.content)
        assertTrue(settings.getValue("showCompletedTodos").jsonPrimitive.boolean)
        assertEquals(AppTheme.DARK.code, settings.getValue("theme").jsonPrimitive.content)
        assertEquals(
            listOf(0, 1),
            root.getValue("categories").jsonArray.map {
                it.jsonObject.getValue("sortOrder").jsonPrimitive.int
            },
        )
        assertEquals(TODO_ID, singleObject(root, "notifications").getValue("todoId").jsonPrimitive.content)
        assertEquals(TODO_ID, singleObject(root, "executions").getValue("todoId").jsonPrimitive.content)
        assertEquals(TODO_ID, singleObject(root, "periodResults").getValue("todoId").jsonPrimitive.content)
        assertEquals(TODO_ID, singleObject(root, "runtimeStates").getValue("todoId").jsonPrimitive.content)
        assertNotNull(singleObject(root, "executions")["snapshot"])
        assertNotNull(singleObject(root, "periodResults")["snapshot"])
    }

    @Test
    fun st026_restoreFailureAtEveryDataStageRollsBackWithoutIntermediateState() = runBlocking {
        seedAllUserData()
        val expected = backedUpState()
        val archive = writeArchive()
        val stagedData = temporaryDataFile()
        val summary = reader.extractAndValidate(ByteArrayInputStream(archive), stagedData)
        val validData = stagedData.readText(Charsets.UTF_8)
        val mutations = listOf(
            "category" to ("\"colorIndex\":2" to "\"colorIndex\":99"),
            "todo" to ("\"repeatType\":\"weekly_count\"" to "\"repeatType\":\"invalid\""),
            "notification" to ("\"relation\":\"at\"" to "\"relation\":\"invalid\""),
            "execution" to ("\"status\":\"completed\"" to "\"status\":\"invalid\""),
            "period result" to (
                "\"completedCount\":1,\"achieved\":true" to
                    "\"completedCount\":0,\"achieved\":true"
                ),
            "runtime state" to (
                "\"appliedDefinitionRevision\":1" to "\"appliedDefinitionRevision\":2"
                ),
        )
        val scheduler = BackupTestNotificationScheduler()
        val restorer = backupRestorer(scheduler)

        mutations.forEach { (stage, replacement) ->
            val corrupted = validData.replaceFirst(replacement.first, replacement.second)
            assertFalse("Missing mutation marker for $stage", corrupted == validData)
            stagedData.writeText(corrupted, Charsets.UTF_8)
            val rollbackArchive = absentTemporaryFile(".mata-backup")
            val rollbackData = absentTemporaryFile(".json")
            val phases = mutableListOf<BackupOperationPhase>()

            val error = runCatching {
                restorer.restore(
                    dataFile = stagedData,
                    summary = summary,
                    rollbackArchive = rollbackArchive,
                    rollbackData = rollbackData,
                ) { phase, _ -> phases += phase }
            }.exceptionOrNull()

            assertTrue(stage, error is BackupFormatException)
            assertEquals(stage, expected, backedUpState())
            assertTrue(stage, BackupOperationPhase.ROLLING_BACK in phases)
            rollbackArchive.delete()
            rollbackData.delete()
        }
        assertEquals(mutations.size, scheduler.reconcileAllCount)
        assertTrue(stagedData.delete())
    }

    @Test
    fun dat008_replayingTheSameRestoreNeverDuplicatesProtectedRecords() = runBlocking {
        seedAllUserData()
        val expected = backedUpState()
        val archive = writeArchive()
        val stagedData = temporaryDataFile()
        val summary = reader.extractAndValidate(ByteArrayInputStream(archive), stagedData)
        val scheduler = BackupTestNotificationScheduler()
        val restorer = backupRestorer(scheduler)

        repeat(2) {
            val rollbackArchive = absentTemporaryFile(BACKUP_EXTENSION)
            val rollbackData = absentTemporaryFile(".json")
            restorer.restore(
                dataFile = stagedData,
                summary = summary,
                rollbackArchive = rollbackArchive,
                rollbackData = rollbackData,
                onProgress = { _, _ -> },
            )

            assertEquals(expected, backedUpState())
            assertEquals(listOf(OPERATION_ID), database.todoExecutionDao().findForTodo(TODO_ID).map {
                it.operationId
            })
            assertEquals(1, database.todoNotificationDao().findForTodo(TODO_ID).size)
            assertEquals(1, database.periodResultDao().findForTodo(TODO_ID).size)
            assertFalse(rollbackArchive.exists())
            assertFalse(rollbackData.exists())
        }

        assertEquals(2, scheduler.reconcileAllCount)
        assertTrue(stagedData.delete())
    }

    @Test
    fun st029_nextLaunchRecoversInterruptedBackupAndRestoreToConsistentState() = runBlocking {
        seedAllUserData()
        val expected = backedUpState()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()

        val createId = UUID.randomUUID().toString()
        val createFiles = operationFiles(context, createId)
        BackupOperationStore(context).also { store ->
            store.clear()
            assertTrue(
                store.start(
                    createId,
                    BackupOperationType.CREATE,
                    "content://com.mochisofts.mata.test/missing-partial-backup",
                ),
            )
        }
        createFiles.directory.mkdirs()
        createFiles.data.writeText("partial")

        val reloadedCreateStore = BackupOperationStore(context)
        BackupCoordinator(context, reloadedCreateStore, clock).recoverInterruptedOperation()

        assertEquals(BackupOperationStatus.FAILED, reloadedCreateStore.state.value.status)
        assertEquals(BackupErrorCode.INCOMPLETE_FILE_REMAINS, reloadedCreateStore.state.value.errorCode)
        assertFalse(createFiles.directory.exists())
        assertEquals(expected, backedUpState())

        reloadedCreateStore.clear()
        val restoreId = UUID.randomUUID().toString()
        val restoreFiles = operationFiles(context, restoreId)
        val originalStore = BackupOperationStore(context)
        assertTrue(
            originalStore.start(
                restoreId,
                BackupOperationType.RESTORE,
                "content://com.mochisofts.mata.test/interrupted-restore",
            ),
        )
        restoreFiles.directory.mkdirs()
        restoreFiles.rollbackArchive.writeText("partial rollback")

        val reloadedRestoreStore = BackupOperationStore(context)
        BackupCoordinator(context, reloadedRestoreStore, clock).recoverInterruptedOperation()

        assertEquals(BackupOperationStatus.FAILED, reloadedRestoreStore.state.value.status)
        assertEquals(BackupErrorCode.STORAGE_UNAVAILABLE, reloadedRestoreStore.state.value.errorCode)
        assertFalse(restoreFiles.directory.exists())
        assertEquals(expected, backedUpState())
    }

    @Test
    fun std05_largeBackupAndRestoreStreamAcrossMultipleDatabasePages() = runBlocking {
        val recordCount = 513
        database.withTransaction {
            repeat(recordCount) { index ->
                val name = "Category $index"
                database.categoryDao().upsert(
                    CategoryEntity(
                        id = UUID(0L, index.toLong() + 1).toString(),
                        name = name,
                        normalizedName = name.lowercase(),
                        colorIndex = index % 16,
                        iconName = "Home",
                        sortOrder = index,
                        createdAt = index.toLong(),
                        updatedAt = index.toLong(),
                    ),
                )
            }
        }
        val archive = File.createTempFile("backup-large-", BACKUP_EXTENSION, context.cacheDir)
        val stagedData = temporaryDataFile()
        val writeProgress = mutableListOf<Int>()

        FileOutputStream(archive).use { output ->
            writer.write(output) { phase, progress ->
                if (phase == BackupOperationPhase.WRITING && progress != null) writeProgress += progress
            }
        }
        val summary = FileInputStream(archive).use { input ->
            reader.extractAndValidate(input, stagedData)
        }
        val sink = CountingBackupSink()
        val parsed = reader.parseValidatedData(stagedData, summary.manifest, sink)

        assertEquals(BackupCounts(recordCount, 0, 0, 0, 0, 0), summary.manifest.counts)
        assertEquals(summary.manifest.counts, parsed.counts)
        assertEquals(summary.manifest.counts, sink.counts())
        assertEquals(recordCount, writeProgress.size)
        assertEquals(100, writeProgress.last())
        assertTrue(writeProgress.zipWithNext().all { (before, after) -> before <= after })
        assertTrue(archive.length() > 0)
        assertTrue(stagedData.length() > 0)
        assertTrue(archive.delete())
        assertTrue(stagedData.delete())
    }

    private suspend fun seedAllUserData() {
        settingsRepository.setDayEndHour(4)
        settingsRepository.setWeekStart(DayOfWeek.SUNDAY)
        settingsRepository.setShowCompleted(true)
        settingsRepository.setTheme(AppTheme.DARK)
        settingsRepository.setTodoListMode("CATEGORY")
        settingsRepository.setNotificationPermissionRequested(true)

        val categories = listOf(
            CategoryEntity(
                id = CATEGORY_ID,
                name = "生活",
                normalizedName = "生活",
                colorIndex = 2,
                iconName = "Home",
                sortOrder = 0,
                createdAt = 10,
                updatedAt = 10,
            ),
            CategoryEntity(
                id = SECOND_CATEGORY_ID,
                name = "ゲーム",
                normalizedName = "ゲーム",
                colorIndex = 5,
                iconName = "SportsEsports",
                sortOrder = 1,
                createdAt = 20,
                updatedAt = 20,
            ),
        )
        categories.forEach { database.categoryDao().upsert(it) }
        val encoded = RecurrenceRuleJson.encode(
            RecurrenceRule(type = RecurrenceType.WEEKLY_COUNT, requiredCount = 1),
        )
        val todo = TodoEntity(
            id = TODO_ID,
            title = "週に一度のTODO",
            description = "バックアップ対象",
            categoryId = CATEGORY_ID,
            startDate = "2026-08-03",
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = 720,
            definitionRevision = 1,
            createdAt = 30,
            updatedAt = 30,
            archivedAt = null,
        )
        database.todoDao().upsert(todo)
        val notification = TodoNotificationEntity(
            id = NOTIFICATION_ID,
            todoId = TODO_ID,
            relation = "at",
            amount = 0,
            unit = "minute",
            sortOrder = 0,
            createdAt = 40,
            updatedAt = 40,
        )
        database.todoNotificationDao().upsertAll(listOf(notification))
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = EXECUTION_ID,
                operationId = OPERATION_ID,
                todoId = TODO_ID,
                logicalDate = "2026-08-04",
                status = "completed",
                actedAt = 50,
                finalizedAt = 50,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = HistorySnapshotJson.encode(
                    todo = todo,
                    category = categories.first(),
                    notifications = listOf(notification),
                    endHour = 4,
                    weekStart = DayOfWeek.SUNDAY,
                    logicalDate = LocalDate.of(2026, 8, 4),
                ),
            ),
        )
        database.periodResultDao().insertBackup(
            PeriodResultEntity(
                id = PERIOD_ID,
                todoId = TODO_ID,
                periodType = RecurrenceType.WEEKLY_COUNT.code,
                periodStart = "2026-08-03",
                periodEnd = "2026-08-08",
                requiredCount = 1,
                completedCount = 1,
                achieved = true,
                displayDate = "2026-08-08",
                finalizedAt = 60,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = HistorySnapshotJson.encode(
                    todo = todo,
                    category = categories.first(),
                    notifications = listOf(notification),
                    endHour = 4,
                    weekStart = DayOfWeek.SUNDAY,
                    periodStart = LocalDate.of(2026, 8, 3),
                    periodEnd = LocalDate.of(2026, 8, 8),
                ),
            ),
        )
        database.todoRuntimeStateDao().upsert(
            TodoRuntimeStateEntity(
                todoId = TODO_ID,
                lastFinalizedLogicalDate = "2026-08-08",
                lastFinalizedWeeklyPeriodEnd = "2026-08-08",
                lastFinalizedMonthlyPeriodEnd = null,
                appliedDefinitionRevision = 1,
                reconciliationCursorDate = null,
                updatedAt = 70,
            ),
        )
    }

    private suspend fun writeArchive(): ByteArray = ByteArrayOutputStream().also { output ->
        writer.write(output)
    }.toByteArray()

    private fun backupRestorer(scheduler: BackupTestNotificationScheduler) = BackupArchiveRestorer(
        database = database,
        categoryDao = database.categoryDao(),
        todoDao = database.todoDao(),
        notificationDao = database.todoNotificationDao(),
        executionDao = database.todoExecutionDao(),
        periodResultDao = database.periodResultDao(),
        runtimeStateDao = database.todoRuntimeStateDao(),
        scheduledNotificationDao = database.scheduledNotificationDao(),
        widgetInstanceStateDao = database.widgetInstanceStateDao(),
        settingsRepository = settingsRepository,
        notificationScheduler = scheduler,
        historyReconciler = NoOpBackupHistoryReconciler(),
        widgetUpdater = WidgetUpdater(context, DiagnosticLogger()),
        writer = writer,
        reader = reader,
    )

    private suspend fun backedUpState() = BackedUpState(
        categories = database.categoryDao().backupPage(database.categoryDao().backupCount(), 0),
        todos = database.todoDao().backupPage(database.todoDao().backupCount(), 0),
        notifications = database.todoNotificationDao().backupPage(database.todoNotificationDao().count(), 0),
        executions = database.todoExecutionDao().backupPage(database.todoExecutionDao().backupCount(), 0),
        periodResults = database.periodResultDao().backupPage(database.periodResultDao().backupCount(), 0),
        runtimeStates = database.todoRuntimeStateDao().backupPage(database.todoRuntimeStateDao().backupCount(), 0),
        settings = settingsRepository.backupSnapshot(),
    )

    private fun absentTemporaryFile(suffix: String): File = File.createTempFile(
        "backup-rollback-",
        suffix,
        context.cacheDir,
    ).also { check(it.delete()) }

    private fun replaceManifest(archive: ByteArray, manifest: String): ByteArray {
        val entries = zipEntries(archive)
        return zip(entries.getValue(DATA_ENTRY), manifest.toByteArray(Charsets.UTF_8))
    }

    private fun replaceData(archive: ByteArray, data: String): ByteArray {
        val entries = zipEntries(archive)
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val originalManifest = entries.getValue(MANIFEST_ENTRY).toString(Charsets.UTF_8)
        val updatedManifest = originalManifest
            .replace(Regex("\"sha256\":\"[0-9a-f]{64}\""), "\"sha256\":\"${sha256(dataBytes)}\"")
            .replace(Regex("\"uncompressedBytes\":[0-9]+"), "\"uncompressedBytes\":${dataBytes.size}")
        return zip(dataBytes, updatedManifest.toByteArray(Charsets.UTF_8))
    }

    private fun zip(data: ByteArray, manifest: ByteArray): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                listOf(DATA_ENTRY to data, MANIFEST_ENTRY to manifest).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = ZIP_ENTRY_TIME })
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun zipEntries(archive: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun parseObject(bytes: ByteArray): JsonObject =
        Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject

    private fun singleObject(root: JsonObject, key: String): JsonObject {
        val values: JsonArray = root.getValue(key).jsonArray
        assertEquals(1, values.size)
        return values.single().jsonObject
    }

    private fun temporaryDataFile(): File = File.createTempFile(
        "backup-spec-",
        ".json",
        context.cacheDir,
    )

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

    private class CountingBackupSink : BackupDataSink {
        private var categories = 0
        private var todos = 0
        private var notifications = 0
        private var executions = 0
        private var periodResults = 0
        private var runtimeStates = 0

        override suspend fun category(value: CategoryEntity) { categories++ }
        override suspend fun todo(value: TodoEntity) { todos++ }
        override suspend fun notification(value: TodoNotificationEntity) { notifications++ }
        override suspend fun execution(value: TodoExecutionEntity) { executions++ }
        override suspend fun periodResult(value: PeriodResultEntity) { periodResults++ }
        override suspend fun runtimeState(value: TodoRuntimeStateEntity) { runtimeStates++ }

        fun counts() = BackupCounts(
            categories,
            todos,
            notifications,
            executions,
            periodResults,
            runtimeStates,
        )
    }

    private data class BackedUpState(
        val categories: List<CategoryEntity>,
        val todos: List<TodoEntity>,
        val notifications: List<TodoNotificationEntity>,
        val executions: List<TodoExecutionEntity>,
        val periodResults: List<PeriodResultEntity>,
        val runtimeStates: List<TodoRuntimeStateEntity>,
        val settings: BackupSettings,
    )

    private class BackupTestNotificationScheduler : NotificationScheduler {
        override val notificationCount = MutableStateFlow(0)
        var reconcileAllCount = 0

        override fun systemState() = NotificationSystemState(
            canPostNotifications = true,
            runtimePermissionRelevant = false,
            runtimePermissionGranted = true,
            exactAlarmRelevant = false,
            canScheduleExactAlarms = true,
        )

        override suspend fun reconcileTodo(todoId: String) = Unit

        override suspend fun reconcileAll() {
            reconcileAllCount++
        }

        override suspend fun cancelTodo(todoId: String) = Unit
    }

    private class NoOpBackupHistoryReconciler : HistoryReconciler {
        override suspend fun reconcile(maxRecords: Int) = HistoryReconciliationResult(
            generatedRecords = 0,
            hasMore = false,
        )
    }

    private companion object {
        const val DATA_ENTRY = "data.json"
        const val MANIFEST_ENTRY = "manifest.json"
        const val ZIP_ENTRY_TIME = 1_700_000_000_000L
        const val CATEGORY_ID = "00000000-0000-0000-0000-000000000001"
        const val SECOND_CATEGORY_ID = "00000000-0000-0000-0000-000000000002"
        const val TODO_ID = "00000000-0000-0000-0000-000000000003"
        const val NOTIFICATION_ID = "00000000-0000-0000-0000-000000000004"
        const val EXECUTION_ID = "00000000-0000-0000-0000-000000000005"
        const val OPERATION_ID = "00000000-0000-0000-0000-000000000006"
        const val PERIOD_ID = "00000000-0000-0000-0000-000000000007"
    }
}
