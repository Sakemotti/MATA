package com.mochisofts.mata.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
