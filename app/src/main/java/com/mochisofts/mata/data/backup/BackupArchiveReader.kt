package com.mochisofts.mata.data.backup

import android.util.JsonReader
import android.util.JsonToken
import com.mochisofts.mata.core.designsystem.CategoryIconOptions
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.repository.HistorySnapshotJson
import com.mochisofts.mata.data.repository.HistorySnapshotV1
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal interface BackupDataSink {
    suspend fun category(value: CategoryEntity)
    suspend fun todo(value: TodoEntity)
    suspend fun notification(value: TodoNotificationEntity)
    suspend fun execution(value: TodoExecutionEntity)
    suspend fun periodResult(value: PeriodResultEntity)
    suspend fun runtimeState(value: TodoRuntimeStateEntity)
}

internal data class ParsedBackupData(
    val settings: BackupSettings,
    val counts: BackupCounts,
    val archivedTodoCount: Int,
)

@Singleton
class BackupArchiveReader @Inject constructor() {
    suspend fun extractAndValidate(
        input: InputStream,
        dataFile: File,
        onProgress: (BackupOperationPhase, Int?) -> Unit = { _, _ -> },
    ): BackupSummary {
        onProgress(BackupOperationPhase.VALIDATING, null)
        val digest = MessageDigest.getInstance("SHA-256")
        var measuredBytes = 0L
        var dataEntry: ZipEntry? = null
        var manifestEntry: ZipEntry? = null
        val manifestBytes: ByteArray
        try {
            ZipInputStream(input.buffered(), StandardCharsets.UTF_8).use { zip ->
                dataEntry = zip.nextEntry ?: invalid("data.json is missing")
                validateEntry(dataEntry!!, DATA_ENTRY)
                FileOutputStream(dataFile).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var first = true
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        if (first) {
                            first = false
                            if (read >= 3 && buffer[0] == UTF8_BOM[0] &&
                                buffer[1] == UTF8_BOM[1] && buffer[2] == UTF8_BOM[2]
                            ) {
                                invalid("BOM is not allowed")
                            }
                        }
                        measuredBytes = checkedAdd(measuredBytes, read.toLong())
                        if (measuredBytes > MAX_DATA_BYTES) invalid("data.json is too large")
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
                FileInputStream(dataFile).use { written ->
                    val prefix = ByteArray(UTF8_BOM.size)
                    if (written.read(prefix) == prefix.size && prefix.contentEquals(UTF8_BOM)) {
                        invalid("BOM is not allowed")
                    }
                }
                zip.closeEntry()
                manifestEntry = zip.nextEntry ?: invalid("manifest.json is missing")
                validateEntry(manifestEntry!!, MANIFEST_ENTRY)
                manifestBytes = readLimited(zip, MAX_MANIFEST_BYTES)
                zip.closeEntry()
                if (zip.nextEntry != null) invalid("Unknown ZIP entry")
            }
        } catch (error: BackupFormatException) {
            dataFile.delete()
            throw error
        } catch (error: Exception) {
            dataFile.delete()
            throw BackupFormatException(message = "Invalid ZIP: ${error.javaClass.simpleName}")
        }
        return try {
            if (dataEntry!!.time != manifestEntry!!.time) invalid("ZIP timestamps differ")
            validateCompressionRatio(dataEntry!!, measuredBytes)
            validateCompressionRatio(manifestEntry!!, manifestBytes.size.toLong())
            val manifest = readManifest(manifestBytes)
            val actualSha = digest.digest().toHex()
            if (manifest.dataSha256 != actualSha || manifest.dataUncompressedBytes != measuredBytes) {
                invalid("data.json digest mismatch")
            }
            val parsed = parseData(dataFile, manifest.counts, null, onProgress)
            BackupSummary(manifest, parsed.archivedTodoCount)
        } catch (error: BackupFormatException) {
            dataFile.delete()
            throw error
        } catch (error: Exception) {
            dataFile.delete()
            throw BackupFormatException(message = "Invalid backup data: ${error.javaClass.simpleName}")
        }
    }

    internal suspend fun parseValidatedData(
        dataFile: File,
        manifest: BackupManifest,
        sink: BackupDataSink,
        onProgress: (BackupOperationPhase, Int?) -> Unit = { _, _ -> },
    ): ParsedBackupData = parseData(dataFile, manifest.counts, sink, onProgress)

    private fun readManifest(bytes: ByteArray): BackupManifest = strictReader(ByteArrayInputStream(bytes)).use {
        it.beginObject()
        it.expectName("formatId")
        if (it.strictString() != BACKUP_FORMAT_ID) invalid("Unknown backup format")
        it.expectName("formatVersion")
        val formatVersion = it.strictInt()
        it.expectName("minimumReaderVersion")
        val minimumReaderVersion = it.strictInt()
        if (formatVersion != BACKUP_FORMAT_VERSION || minimumReaderVersion > BACKUP_FORMAT_VERSION) {
            throw BackupFormatException(BackupErrorCode.UNSUPPORTED_VERSION, "Unsupported backup version")
        }
        it.expectName("backupId")
        val backupId = it.uuid()
        it.expectName("createdAt")
        val createdAt = it.strictLong()
        it.expectName("appVersionName")
        val appVersionName = it.strictString().also { value ->
            if (value.codePointLength() > 100) invalid("App version name is too long")
        }
        it.expectName("appVersionCode")
        val appVersionCode = it.strictLong().also { value -> if (value < 0) invalid("Invalid version code") }
        it.expectName("roomSchemaVersion")
        val roomVersion = it.strictInt().also { value -> if (value < 1) invalid("Invalid Room version") }
        it.expectName("data")
        it.beginObject()
        it.expectName("sha256")
        val sha = it.strictString()
        if (!SHA_PATTERN.matches(sha)) invalid("Invalid SHA-256")
        it.expectName("uncompressedBytes")
        val bytesCount = it.strictLong()
        if (bytesCount !in 0..MAX_DATA_BYTES) invalid("Invalid data size")
        it.requireObjectEnd()
        it.expectName("counts")
        val counts = it.readCounts()
        it.requireObjectEnd()
        it.requireDocumentEnd()
        BackupManifest(
            backupId = backupId,
            createdAt = createdAt,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            roomSchemaVersion = roomVersion,
            dataSha256 = sha,
            dataUncompressedBytes = bytesCount,
            counts = counts,
        )
    }

    private suspend fun parseData(
        file: File,
        expectedCounts: BackupCounts,
        sink: BackupDataSink?,
        onProgress: (BackupOperationPhase, Int?) -> Unit,
    ): ParsedBackupData = strictReader(FileInputStream(file)).use { reader ->
        val validation = ValidationContext(expectedCounts)
        reader.beginObject()
        reader.expectName("formatVersion")
        if (reader.strictInt() != BACKUP_FORMAT_VERSION) invalid("data.json version mismatch")
        reader.expectName("settings")
        val settings = reader.readSettings()
        reader.expectName("categories")
        reader.beginArray()
        while (reader.hasNext()) {
            val entity = reader.readCategory(validation)
            sink?.category(entity)
            validation.progress(onProgress)
        }
        reader.endArray()
        reader.expectName("todos")
        reader.beginArray()
        while (reader.hasNext()) {
            val entity = reader.readTodo(validation)
            sink?.todo(entity)
            validation.progress(onProgress)
        }
        reader.endArray()
        reader.expectName("notifications")
        reader.beginArray()
        while (reader.hasNext()) {
            val entity = reader.readNotification(validation)
            sink?.notification(entity)
            validation.progress(onProgress)
        }
        reader.endArray()
        reader.expectName("executions")
        reader.beginArray()
        while (reader.hasNext()) {
            val entity = reader.readExecution(validation)
            sink?.execution(entity)
            validation.progress(onProgress)
        }
        reader.endArray()
        reader.expectName("periodResults")
        reader.beginArray()
        while (reader.hasNext()) {
            val entity = reader.readPeriodResult(validation)
            sink?.periodResult(entity)
            validation.progress(onProgress)
        }
        reader.endArray()
        reader.expectName("runtimeStates")
        reader.beginArray()
        while (reader.hasNext()) {
            val entity = reader.readRuntimeState(validation)
            sink?.runtimeState(entity)
            validation.progress(onProgress)
        }
        reader.endArray()
        reader.requireObjectEnd()
        reader.requireDocumentEnd()
        validation.finish()
        ParsedBackupData(settings, validation.actualCounts(), validation.archivedTodoCount)
    }

    private fun JsonReader.readSettings(): BackupSettings {
        beginObject()
        expectName("uncategorizedEndHour")
        val endHour = strictInt().also { if (it !in 0..23) invalid("Invalid end hour") }
        expectName("weekStartDay")
        val weekStart = weekday(strictString())
        expectName("showCompletedTodos")
        val showCompleted = strictBoolean()
        expectName("theme")
        val themeCode = strictString()
        val theme = AppTheme.entries.firstOrNull { it.code == themeCode } ?: invalid("Invalid theme")
        requireObjectEnd()
        return BackupSettings(endHour, weekStart, showCompleted, theme)
    }

    private fun JsonReader.readCategory(context: ValidationContext): CategoryEntity {
        beginObject()
        expectName("id")
        val id = uuid()
        expectName("name")
        val name = strictString().boundedCodePoints(1, 30, "category name")
        expectName("normalizedName")
        val normalizedName = strictString()
        val expectedNormalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        if (normalizedName != expectedNormalized || !context.categoryNames.add(normalizedName)) {
            invalid("Invalid or duplicate normalized category name")
        }
        expectName("colorIndex")
        val color = strictInt().also { if (it !in 0..15) invalid("Invalid category color") }
        expectName("iconKey")
        val icon = strictString()
        if (icon !in ALLOWED_ICONS) invalid("Invalid category icon")
        expectName("sortOrder")
        val sort = strictInt()
        if (sort != context.categories) invalid("Category order must be contiguous")
        expectName("endHour")
        val endHour = strictInt().also { if (it !in 0..23) invalid("Invalid category end hour") }
        expectName("createdAt")
        val createdAt = strictLong()
        expectName("updatedAt")
        val updatedAt = strictLong()
        if (updatedAt < createdAt) invalid("Category update time precedes creation")
        requireObjectEnd()
        if (!context.categoryIds.add(id)) invalid("Duplicate category ID")
        context.categories++
        return CategoryEntity(id, name, normalizedName, color, icon, endHour, sort, createdAt, updatedAt)
    }

    private fun JsonReader.readTodo(context: ValidationContext): TodoEntity {
        beginObject()
        expectName("id")
        val id = uuid()
        expectName("title")
        val title = strictString().boundedCodePoints(1, 100, "TODO title")
        expectName("description")
        val description = strictString().boundedCodePoints(0, 1000, "TODO description")
        expectName("categoryId")
        val categoryId = nullableString()?.also { value ->
            requireCanonicalUuid(value)
            if (value !in context.categoryIds) invalid("Unknown category reference")
        }
        expectName("startDate")
        val startDate = isoDate(strictString())
        expectName("endDate")
        val endDate = nullableString()?.let(::isoDate)
        if (endDate != null && endDate.isBefore(startDate)) invalid("TODO end date precedes start date")
        expectName("repeatType")
        val typeCode = strictString()
        val type = RecurrenceType.entries.firstOrNull { it.code == typeCode } ?: invalid("Invalid repeat type")
        expectName("repeatParamsVersion")
        val paramsVersion = strictInt()
        if (paramsVersion != 1) throw BackupFormatException(
            BackupErrorCode.UNSUPPORTED_VERSION,
            "Unsupported repeat parameters",
        )
        expectName("repeatParams")
        val rule = readRepeatParams(type)
        if (!rule.isValid()) invalid("Invalid repeat parameters")
        val encoded = RecurrenceRuleJson.encode(rule)
        expectName("deadlineMinute")
        val due = nullableInt()?.also { if (it !in 0..1439) invalid("Invalid deadline") }
        expectName("definitionRevision")
        val revision = strictInt().also { if (it < 1) invalid("Invalid TODO revision") }
        expectName("archivedAt")
        val archivedAt = nullableLong()
        expectName("createdAt")
        val createdAt = strictLong()
        expectName("updatedAt")
        val updatedAt = strictLong()
        if (updatedAt < createdAt) invalid("TODO update time precedes creation")
        requireObjectEnd()
        if (!context.todoIds.add(id)) invalid("Duplicate TODO ID")
        val order = createdAt to id
        if (context.lastTodoOrder?.let { previous ->
                previous.first > order.first ||
                    (previous.first == order.first && previous.second > order.second)
            } == true
        ) {
            invalid("TODO array is not canonical")
        }
        context.lastTodoOrder = order
        context.todoRevisions[id] = revision
        if (archivedAt != null) context.archivedTodoCount++
        context.todos++
        return TodoEntity(
            id = id,
            title = title,
            description = description,
            categoryId = categoryId,
            startDate = startDate.toString(),
            endDate = endDate?.toString(),
            recurrenceType = typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = due,
            definitionRevision = revision,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archivedAt = archivedAt,
        )
    }

    private fun JsonReader.readRepeatParams(type: RecurrenceType): RecurrenceRule {
        beginObject()
        val rule = when (type) {
            RecurrenceType.SELECTED_WEEKDAYS -> {
                expectName("weekdays")
                beginArray()
                val days = mutableListOf<DayOfWeek>()
                while (hasNext()) days += weekday(strictString())
                endArray()
                if (days.isEmpty() || days.distinct().size != days.size ||
                    days != days.sortedBy(DayOfWeek::getValue)
                ) {
                    invalid("Invalid weekday list")
                }
                RecurrenceRule(type, selectedWeekdays = days.toSet())
            }
            RecurrenceType.MONTHLY_DAY -> {
                expectName("day")
                RecurrenceRule(type, monthlyDay = strictInt())
            }
            RecurrenceType.MONTHLY_NTH_WEEKDAYS -> {
                expectName("nthWeekdays")
                beginArray()
                val nthWeekdays = mutableListOf<MonthlyNthWeekday>()
                while (hasNext()) {
                    beginObject()
                    expectName("ordinal")
                    val ordinal = strictInt()
                    expectName("weekday")
                    val dayOfWeek = weekday(strictString())
                    requireObjectEnd()
                    nthWeekdays += MonthlyNthWeekday(ordinal, dayOfWeek)
                }
                endArray()
                val canonical = nthWeekdays.sortedWith(
                    compareBy(MonthlyNthWeekday::ordinal, { it.dayOfWeek.value }),
                )
                if (nthWeekdays.isEmpty() || nthWeekdays.distinct().size != nthWeekdays.size ||
                    nthWeekdays != canonical || nthWeekdays.any { !it.isValid() }
                ) {
                    invalid("Invalid monthly nth weekday list")
                }
                RecurrenceRule(type, monthlyNthWeekdays = nthWeekdays.toSet())
            }
            RecurrenceType.EVERY_N_DAYS -> {
                expectName("intervalDays")
                RecurrenceRule(type, intervalDays = strictInt())
            }
            RecurrenceType.WEEKLY_COUNT,
            RecurrenceType.MONTHLY_COUNT,
            -> {
                expectName("requiredCount")
                RecurrenceRule(type, requiredCount = strictInt())
            }
            else -> RecurrenceRule(type)
        }
        requireObjectEnd()
        return rule
    }

    private fun JsonReader.readNotification(context: ValidationContext): TodoNotificationEntity {
        beginObject()
        expectName("id")
        val id = uuid()
        expectName("todoId")
        val todoId = uuid().also { if (it !in context.todoIds) invalid("Unknown notification TODO") }
        expectName("relation")
        val relation = strictString()
        val relationValue = NotificationRelation.entries.firstOrNull { it.code == relation }
            ?: invalid("Invalid notification relation")
        expectName("amount")
        val amount = strictInt()
        expectName("unit")
        val unit = nullableString()
        val internalUnit = if (relationValue == NotificationRelation.AT) {
            if (amount != 0 || unit != null) invalid("Invalid at-deadline notification")
            NotificationUnit.MINUTE
        } else {
            if (amount !in 1..999 || unit == null) invalid("Invalid notification amount")
            NotificationUnit.entries.firstOrNull { it.code == unit } ?: invalid("Invalid notification unit")
        }
        expectName("sortOrder")
        val sortOrder = strictInt()
        val expectedOrder = context.nextNotificationOrder.getOrDefault(todoId, 0)
        if (sortOrder != expectedOrder || expectedOrder >= 10) invalid("Invalid notification order")
        context.nextNotificationOrder[todoId] = expectedOrder + 1
        val timing = relation to (amount * internalUnit.nominalMinutes)
        if (!context.notificationTimings.getOrPut(todoId, ::mutableSetOf).add(timing)) {
            invalid("Duplicate notification timing")
        }
        expectName("createdAt")
        val createdAt = strictLong()
        expectName("updatedAt")
        val updatedAt = strictLong()
        if (updatedAt < createdAt) invalid("Notification update time precedes creation")
        requireObjectEnd()
        if (!context.notificationIds.add(id)) invalid("Duplicate notification ID")
        context.notifications++
        return TodoNotificationEntity(
            id,
            todoId,
            relation,
            amount,
            internalUnit.code,
            sortOrder,
            createdAt,
            updatedAt,
        )
    }

    private fun JsonReader.readExecution(context: ValidationContext): TodoExecutionEntity {
        beginObject()
        expectName("id")
        val id = uuid()
        expectName("operationId")
        val operationId = uuid()
        expectName("todoId")
        val todoId = uuid().also { if (it !in context.todoIds) invalid("Unknown execution TODO") }
        expectName("logicalDate")
        val logicalDate = isoDate(strictString()).toString()
        expectName("status")
        val status = strictString()
        if (status !in EXECUTION_STATUSES) invalid("Invalid execution status")
        expectName("actedAt")
        val actedAt = nullableLong()
        if ((status == "missed") != (actedAt == null)) invalid("Invalid execution action time")
        expectName("finalizedAt")
        val finalizedAt = strictLong()
        expectName("definitionRevision")
        val revision = strictInt()
        if (revision !in 1..context.todoRevisions.getValue(todoId)) invalid("Invalid execution revision")
        expectName("snapshotVersion")
        val snapshotVersion = strictInt()
        expectName("snapshot")
        val snapshot = readJsonObject()
        validateExecutionSnapshot(snapshot, snapshotVersion, todoId, revision, logicalDate)
        requireObjectEnd()
        if (!context.executionIds.add(id) || !context.operationIds.add(operationId) ||
            !context.executionKeys.add(todoId to logicalDate)
        ) {
            invalid("Duplicate execution")
        }
        context.executions++
        return TodoExecutionEntity(
            id,
            operationId,
            todoId,
            logicalDate,
            status,
            actedAt,
            finalizedAt,
            revision,
            snapshotVersion,
            snapshot.compact(),
        )
    }

    private fun JsonReader.readPeriodResult(context: ValidationContext): PeriodResultEntity {
        beginObject()
        expectName("id")
        val id = uuid()
        expectName("todoId")
        val todoId = uuid().also { if (it !in context.todoIds) invalid("Unknown period TODO") }
        expectName("periodType")
        val type = strictString()
        if (type !in PERIOD_TYPES) invalid("Invalid period type")
        expectName("periodStart")
        val start = isoDate(strictString())
        expectName("periodEnd")
        val end = isoDate(strictString())
        if (end.isBefore(start)) invalid("Invalid period boundary")
        expectName("requiredCount")
        val required = strictInt()
        if (required !in if (type == "weekly_count") 1..7 else 1..31) invalid("Invalid required count")
        expectName("completedCount")
        val completed = strictInt().also { if (it < 0) invalid("Invalid completed count") }
        expectName("achieved")
        val achieved = strictBoolean()
        if (achieved != (completed >= required)) invalid("Invalid achieved result")
        expectName("displayDate")
        val display = isoDate(strictString())
        if (display !in start..end) invalid("Invalid period display date")
        expectName("finalizedAt")
        val finalizedAt = strictLong()
        expectName("definitionRevision")
        val revision = strictInt()
        if (revision !in 1..context.todoRevisions.getValue(todoId)) invalid("Invalid period revision")
        expectName("snapshotVersion")
        val snapshotVersion = strictInt()
        expectName("snapshot")
        val snapshot = readJsonObject()
        validatePeriodSnapshot(snapshot, snapshotVersion, todoId, revision, start, end)
        requireObjectEnd()
        val periodKey = Triple(todoId, start.toString(), end.toString())
        if (!context.periodIds.add(id) || !context.periodKeys.add(periodKey)) invalid("Duplicate period result")
        context.periodResults++
        return PeriodResultEntity(
            id,
            todoId,
            type,
            start.toString(),
            end.toString(),
            required,
            completed,
            achieved,
            display.toString(),
            finalizedAt,
            revision,
            snapshotVersion,
            snapshot.compact(),
        )
    }

    private fun JsonReader.readRuntimeState(context: ValidationContext): TodoRuntimeStateEntity {
        beginObject()
        expectName("todoId")
        val todoId = uuid().also { if (it !in context.todoIds) invalid("Unknown runtime TODO") }
        expectName("lastFinalizedLogicalDate")
        val lastDate = nullableString()?.let { isoDate(it).toString() }
        expectName("lastFinalizedWeeklyPeriodEnd")
        val weeklyEnd = nullableString()?.let { isoDate(it).toString() }
        expectName("lastFinalizedMonthlyPeriodEnd")
        val monthlyEnd = nullableString()?.let { isoDate(it).toString() }
        expectName("appliedDefinitionRevision")
        val revision = strictInt()
        if (revision !in 1..context.todoRevisions.getValue(todoId)) invalid("Invalid runtime revision")
        expectName("reconciliationCursorDate")
        val cursor = nullableString()?.let { isoDate(it).toString() }
        expectName("updatedAt")
        val updatedAt = strictLong()
        requireObjectEnd()
        if (!context.runtimeTodoIds.add(todoId)) invalid("Duplicate runtime state")
        context.runtimeStates++
        return TodoRuntimeStateEntity(todoId, lastDate, weeklyEnd, monthlyEnd, revision, cursor, updatedAt)
    }

    private fun validateExecutionSnapshot(
        element: JsonObject,
        version: Int,
        todoId: String,
        revision: Int,
        logicalDate: String,
    ) {
        val snapshot = HistorySnapshotJson.decode(element.compact()) ?: invalid("Invalid execution snapshot")
        validateCommonSnapshot(snapshot)
        if (version != 1 || snapshot.version != version || snapshot.todoId != todoId ||
            snapshot.definitionRevision != revision || snapshot.logicalDate != logicalDate ||
            snapshot.periodStart != null || snapshot.periodEnd != null
        ) {
            invalid("Execution snapshot does not match its record")
        }
    }

    private fun validatePeriodSnapshot(
        element: JsonObject,
        version: Int,
        todoId: String,
        revision: Int,
        start: LocalDate,
        end: LocalDate,
    ) {
        val snapshot = HistorySnapshotJson.decode(element.compact()) ?: invalid("Invalid period snapshot")
        validateCommonSnapshot(snapshot)
        if (version != 1 || snapshot.version != version || snapshot.todoId != todoId ||
            snapshot.definitionRevision != revision || snapshot.logicalDate != null ||
            snapshot.periodStart != start.toString() || snapshot.periodEnd != end.toString()
        ) {
            invalid("Period snapshot does not match its record")
        }
    }

    private fun validateCommonSnapshot(snapshot: HistorySnapshotV1) {
        requireCanonicalUuid(snapshot.todoId)
        snapshot.title.boundedCodePoints(1, 100, "snapshot title")
        snapshot.description.boundedCodePoints(0, 1000, "snapshot description")
        val start = isoDate(snapshot.startDate)
        val end = snapshot.endDate?.let(::isoDate)
        if (end != null && end.isBefore(start)) invalid("Invalid snapshot dates")
        val type = RecurrenceType.entries.firstOrNull { it.code == snapshot.recurrenceType }
            ?: invalid("Invalid snapshot repeat type")
        val rule = runCatching {
            RecurrenceRuleJson.decode(type.code, snapshot.repeatParamsVersion, snapshot.repeatParamsJson)
        }.getOrNull() ?: invalid("Invalid snapshot repeat parameters")
        if (!rule.isValid() || snapshot.dueMinutes?.let { it !in 0..1439 } == true ||
            snapshot.definitionRevision < 1 || snapshot.endHour !in 0..23 || snapshot.weekStart !in 1..7
        ) {
            invalid("Invalid snapshot values")
        }
        snapshot.categoryId?.let(::requireCanonicalUuid)
        if (snapshot.categoryColorIndex?.let { it !in 0..15 } == true ||
            snapshot.categoryIconName?.let { it !in ALLOWED_ICONS } == true ||
            snapshot.notifications.size > 10
        ) {
            invalid("Invalid snapshot category or notifications")
        }
        snapshot.notifications.forEach { notification ->
            val relation = NotificationRelation.entries.firstOrNull { it.code == notification.relation }
                ?: invalid("Invalid snapshot notification relation")
            if (NotificationUnit.entries.none { it.code == notification.unit } ||
                (relation == NotificationRelation.AT && notification.amount != 0) ||
                (relation != NotificationRelation.AT && notification.amount !in 1..999)
            ) {
                invalid("Invalid snapshot notification")
            }
        }
    }

    private fun JsonReader.readJsonObject(): JsonObject {
        val element = readElement(0)
        return element as? JsonObject ?: invalid("Expected JSON object")
    }

    private fun JsonReader.readElement(depth: Int): JsonElement {
        if (depth > MAX_JSON_DEPTH) invalid("JSON nesting is too deep")
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> {
                beginObject()
                val fields = linkedMapOf<String, JsonElement>()
                while (hasNext()) {
                    val name = nextName()
                    if (fields.containsKey(name)) invalid("Duplicate JSON key")
                    fields[name] = readElement(depth + 1)
                }
                endObject()
                JsonObject(fields)
            }
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                val values = mutableListOf<JsonElement>()
                while (hasNext()) values += readElement(depth + 1)
                endArray()
                JsonArray(values)
            }
            JsonToken.STRING -> JsonPrimitive(nextString())
            JsonToken.NUMBER -> JsonPrimitive(strictLong())
            JsonToken.BOOLEAN -> JsonPrimitive(nextBoolean())
            JsonToken.NULL -> {
                nextNull()
                JsonNull
            }
            else -> invalid("Unexpected JSON token")
        }
    }

    private fun JsonReader.readCounts(): BackupCounts {
        beginObject()
        expectName("categories")
        val categories = nonNegativeCount()
        expectName("todos")
        val todos = nonNegativeCount()
        expectName("notifications")
        val notifications = nonNegativeCount()
        expectName("executions")
        val executions = nonNegativeCount()
        expectName("periodResults")
        val periodResults = nonNegativeCount()
        expectName("runtimeStates")
        val runtimeStates = nonNegativeCount()
        requireObjectEnd()
        val counts = BackupCounts(categories, todos, notifications, executions, periodResults, runtimeStates)
        return counts
    }

    private fun JsonReader.nonNegativeCount(): Int = strictInt().also {
        if (it < 0) invalid("Negative record count")
    }

    private fun JsonReader.expectName(expected: String) {
        if (!hasNext() || nextName() != expected) invalid("Expected field $expected")
    }

    private fun JsonReader.requireObjectEnd() {
        if (hasNext()) invalid("Unknown or duplicate JSON field")
        endObject()
    }

    private fun JsonReader.requireDocumentEnd() {
        if (peek() != JsonToken.END_DOCUMENT) invalid("Trailing JSON content")
    }

    private fun JsonReader.strictString(): String {
        if (peek() != JsonToken.STRING) invalid("Expected string")
        return nextString()
    }

    private fun JsonReader.strictBoolean(): Boolean {
        if (peek() != JsonToken.BOOLEAN) invalid("Expected boolean")
        return nextBoolean()
    }

    private fun JsonReader.strictInt(): Int {
        val value = strictLong()
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalid("Integer is out of range")
        return value.toInt()
    }

    private fun JsonReader.strictLong(): Long {
        if (peek() != JsonToken.NUMBER) invalid("Expected integer")
        val raw = nextString()
        if (!INTEGER_PATTERN.matches(raw)) invalid("Invalid integer representation")
        return raw.toLongOrNull() ?: invalid("Integer is out of range")
    }

    private fun JsonReader.nullableString(): String? = if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        strictString()
    }

    private fun JsonReader.nullableLong(): Long? = if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        strictLong()
    }

    private fun JsonReader.nullableInt(): Int? = nullableLong()?.let {
        if (it !in Int.MIN_VALUE..Int.MAX_VALUE) invalid("Integer is out of range")
        it.toInt()
    }

    private fun JsonReader.uuid(): String = strictString().also(::requireCanonicalUuid)

    private fun strictReader(input: InputStream): JsonReader {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(input, decoder)).apply { isLenient = false }
    }

    private fun validateEntry(entry: ZipEntry, expectedName: String) {
        if (entry.name != expectedName || entry.isDirectory || entry.method != ZipEntry.DEFLATED ||
            entry.name.contains('/') || entry.name.contains('\\') || entry.name.contains("..") ||
            entry.size > if (expectedName == DATA_ENTRY) MAX_DATA_BYTES else MAX_MANIFEST_BYTES
        ) {
            invalid("Invalid ZIP entry")
        }
    }

    private fun validateCompressionRatio(entry: ZipEntry, actualSize: Long) {
        val compressed = entry.compressedSize
        if (actualSize >= MIN_RATIO_CHECK_BYTES && compressed > 0 && actualSize / compressed > MAX_RATIO) {
            invalid("Unsafe compression ratio")
        }
    }

    private fun readLimited(input: InputStream, maximum: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total = checkedAdd(total, read.toLong())
            if (total > maximum) invalid("ZIP entry is too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun checkedAdd(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        invalid("Size overflow")
    }

    private fun requireCanonicalUuid(value: String) {
        if (value.length != 36 || runCatching { UUID.fromString(value).toString() }.getOrNull() != value) {
            invalid("Invalid UUID")
        }
    }

    private fun isoDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }
        .getOrNull()
        ?.takeIf { it.toString() == value }
        ?: invalid("Invalid ISO date")

    private fun weekday(code: String): DayOfWeek = DayOfWeek.entries
        .firstOrNull { it.name.lowercase() == code }
        ?: invalid("Invalid weekday")

    private fun String.boundedCodePoints(minimum: Int, maximum: Int, label: String): String {
        val length = codePointLength()
        if (length !in minimum..maximum) invalid("Invalid $label length")
        return this
    }

    private fun String.codePointLength(): Int = codePointCount(0, length)

    private fun JsonObject.compact(): String = Json.encodeToString<JsonElement>(this)

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private class ValidationContext(private val expected: BackupCounts) {
        val categoryIds = mutableSetOf<String>()
        val categoryNames = mutableSetOf<String>()
        val todoIds = mutableSetOf<String>()
        val todoRevisions = mutableMapOf<String, Int>()
        val notificationIds = mutableSetOf<String>()
        val nextNotificationOrder = mutableMapOf<String, Int>()
        val notificationTimings = mutableMapOf<String, MutableSet<Pair<String, Int>>>()
        val executionIds = mutableSetOf<String>()
        val operationIds = mutableSetOf<String>()
        val executionKeys = mutableSetOf<Pair<String, String>>()
        val periodIds = mutableSetOf<String>()
        val periodKeys = mutableSetOf<Triple<String, String, String>>()
        val runtimeTodoIds = mutableSetOf<String>()
        var lastTodoOrder: Pair<Long, String>? = null
        var categories = 0
        var todos = 0
        var notifications = 0
        var executions = 0
        var periodResults = 0
        var runtimeStates = 0
        var archivedTodoCount = 0
        private var processed = 0L

        fun progress(onProgress: (BackupOperationPhase, Int?) -> Unit) {
            processed++
            val value = if (expected.totalRecords == 0L) 100 else (processed * 100 / expected.totalRecords).toInt()
            onProgress(BackupOperationPhase.VALIDATING, value.coerceIn(0, 100))
        }

        fun actualCounts() = BackupCounts(
            categories,
            todos,
            notifications,
            executions,
            periodResults,
            runtimeStates,
        )

        fun finish() {
            if (actualCounts() != expected) invalid("Manifest record counts do not match data")
        }
    }

    private companion object {
        const val DATA_ENTRY = "data.json"
        const val MANIFEST_ENTRY = "manifest.json"
        const val MAX_MANIFEST_BYTES = 64L * 1024
        const val MAX_DATA_BYTES = 1024L * 1024 * 1024
        const val MAX_JSON_DEPTH = 64
        const val MIN_RATIO_CHECK_BYTES = 1024L * 1024
        const val MAX_RATIO = 200
        val INTEGER_PATTERN = Regex("-?(0|[1-9][0-9]*)")
        val SHA_PATTERN = Regex("[0-9a-f]{64}")
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val ALLOWED_ICONS = CategoryIconOptions.mapTo(mutableSetOf()) { it.id }
        val EXECUTION_STATUSES = setOf("completed", "skipped", "missed")
        val PERIOD_TYPES = setOf("weekly_count", "monthly_count")
    }
}

private fun invalid(message: String): Nothing = throw BackupFormatException(message = message)
