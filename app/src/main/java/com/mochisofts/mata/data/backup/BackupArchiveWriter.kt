package com.mochisofts.mata.data.backup

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.JsonWriter
import androidx.room.withTransaction
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultDao
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateDao
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.repository.DataStoreSettingsRepository
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.RecurrenceType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.DayOfWeek
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

@Singleton
class BackupArchiveWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MataDatabase,
    private val categoryDao: CategoryDao,
    private val todoDao: TodoDao,
    private val notificationDao: TodoNotificationDao,
    private val executionDao: TodoExecutionDao,
    private val periodResultDao: PeriodResultDao,
    private val runtimeStateDao: TodoRuntimeStateDao,
    private val settingsRepository: DataStoreSettingsRepository,
    private val clock: Clock,
) {
    suspend fun write(
        output: OutputStream,
        onProgress: (BackupOperationPhase, Int?) -> Unit = { _, _ -> },
    ): BackupManifest {
        val createdAt = clock.millis()
        val settings = settingsRepository.backupSnapshot()
        return database.withTransaction {
            val counts = BackupCounts(
                categories = categoryDao.backupCount(),
                todos = todoDao.backupCount(),
                notifications = notificationDao.count(),
                executions = executionDao.backupCount(),
                periodResults = periodResultDao.backupCount(),
                runtimeStates = runtimeStateDao.backupCount(),
            )
            val version = appVersion()
            ZipOutputStream(output.buffered()).use { zip ->
                val entryTime = createdAt
                zip.putNextEntry(ZipEntry(DATA_ENTRY).apply { time = entryTime })
                val measuredOutput = DigestCountingOutputStream(zip)
                writeData(measuredOutput, settings, counts, onProgress)
                measuredOutput.flush()
                zip.closeEntry()

                val manifest = BackupManifest(
                    backupId = UUID.randomUUID().toString(),
                    createdAt = createdAt,
                    appVersionName = version.first,
                    appVersionCode = version.second,
                    roomSchemaVersion = MataDatabase.SCHEMA_VERSION,
                    dataSha256 = measuredOutput.sha256(),
                    dataUncompressedBytes = measuredOutput.byteCount,
                    counts = counts,
                )
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY).apply { time = entryTime })
                writeManifest(NonClosingOutputStream(zip), manifest)
                zip.closeEntry()
                zip.finish()
                manifest
            }
        }
    }

    private suspend fun writeData(
        output: OutputStream,
        settings: BackupSettings,
        counts: BackupCounts,
        onProgress: (BackupOperationPhase, Int?) -> Unit,
    ) {
        val writer = JsonWriter(OutputStreamWriter(NonClosingOutputStream(output), StandardCharsets.UTF_8))
        var written = 0L
        fun progressed() {
            written += 1
            val progress = if (counts.totalRecords == 0L) 100 else (written * 100 / counts.totalRecords).toInt()
            onProgress(BackupOperationPhase.WRITING, progress)
        }
        writer.beginObject()
        writer.name("formatVersion").value(BACKUP_FORMAT_VERSION.toLong())
        writer.name("settings")
        writer.beginObject()
        writer.name("uncategorizedEndHour").value(settings.uncategorizedEndHour.toLong())
        writer.name("weekStartDay").value(settings.weekStartDay.backupCode())
        writer.name("showCompletedTodos").value(settings.showCompletedTodos)
        writer.name("theme").value(settings.theme.code)
        writer.endObject()

        writer.name("categories").beginArray()
        page(counts.categories, categoryDao::backupPage) { item ->
            writer.writeCategory(item)
            progressed()
        }
        writer.endArray()

        writer.name("todos").beginArray()
        page(counts.todos, todoDao::backupPage) { item ->
            writer.writeTodo(item)
            progressed()
        }
        writer.endArray()

        writer.name("notifications").beginArray()
        page(counts.notifications, notificationDao::backupPage) { item ->
            writer.writeNotification(item)
            progressed()
        }
        writer.endArray()

        writer.name("executions").beginArray()
        page(counts.executions, executionDao::backupPage) { item ->
            writer.writeExecution(item)
            progressed()
        }
        writer.endArray()

        writer.name("periodResults").beginArray()
        page(counts.periodResults, periodResultDao::backupPage) { item ->
            writer.writePeriodResult(item)
            progressed()
        }
        writer.endArray()

        writer.name("runtimeStates").beginArray()
        page(counts.runtimeStates, runtimeStateDao::backupPage) { item ->
            writer.writeRuntimeState(item)
            progressed()
        }
        writer.endArray()
        writer.endObject()
        writer.close()
        if (counts.totalRecords == 0L) onProgress(BackupOperationPhase.WRITING, 100)
    }

    private suspend fun <T> page(
        count: Int,
        loader: suspend (Int, Int) -> List<T>,
        consume: (T) -> Unit,
    ) {
        var offset = 0
        while (offset < count) {
            val items = loader(PAGE_SIZE, offset)
            if (items.isEmpty()) throw IllegalStateException("Backup snapshot changed while writing")
            items.forEach(consume)
            offset += items.size
        }
        if (offset != count) throw IllegalStateException("Backup count mismatch")
    }

    private fun JsonWriter.writeCategory(value: CategoryEntity) {
        beginObject()
        name("id").value(value.id)
        name("name").value(value.name)
        name("normalizedName").value(value.normalizedName)
        name("colorIndex").value(value.colorIndex.toLong())
        name("iconKey").value(value.iconName)
        name("sortOrder").value(value.sortOrder.toLong())
        name("endHour").value(value.endHour.toLong())
        name("createdAt").value(value.createdAt)
        name("updatedAt").value(value.updatedAt)
        endObject()
    }

    private fun JsonWriter.writeTodo(value: TodoEntity) {
        val rule = RecurrenceRuleJson.decode(
            value.recurrenceType,
            value.repeatParamsVersion,
            value.repeatParamsJson,
        )
        beginObject()
        name("id").value(value.id)
        name("title").value(value.title)
        name("description").value(value.description)
        name("categoryId").nullable(value.categoryId)
        name("startDate").value(value.startDate)
        name("endDate").nullable(value.endDate)
        name("repeatType").value(value.recurrenceType)
        name("repeatParamsVersion").value(value.repeatParamsVersion.toLong())
        name("repeatParams").beginObject()
        when (rule.type) {
            RecurrenceType.SELECTED_WEEKDAYS -> {
                name("weekdays").beginArray()
                rule.selectedWeekdays.sortedBy(DayOfWeek::getValue).forEach { value(it.backupCode()) }
                endArray()
            }
            RecurrenceType.MONTHLY_DAY -> name("day").value(rule.monthlyDay!!.toLong())
            RecurrenceType.MONTHLY_NTH_WEEKDAYS -> {
                name("nthWeekdays").beginArray()
                rule.monthlyNthWeekdays
                    .sortedWith(compareBy(MonthlyNthWeekday::ordinal, { it.dayOfWeek.value }))
                    .forEach { value ->
                        beginObject()
                        name("ordinal").value(value.ordinal.toLong())
                        name("weekday").value(value.dayOfWeek.backupCode())
                        endObject()
                    }
                endArray()
            }
            RecurrenceType.EVERY_N_DAYS -> name("intervalDays").value(rule.intervalDays!!.toLong())
            RecurrenceType.WEEKLY_COUNT,
            RecurrenceType.MONTHLY_COUNT,
            -> name("requiredCount").value(rule.requiredCount!!.toLong())
            else -> Unit
        }
        endObject()
        name("deadlineMinute").nullable(value.dueMinutes)
        name("definitionRevision").value(value.definitionRevision.toLong())
        name("archivedAt").nullable(value.archivedAt)
        name("createdAt").value(value.createdAt)
        name("updatedAt").value(value.updatedAt)
        endObject()
    }

    private fun JsonWriter.writeNotification(value: TodoNotificationEntity) {
        beginObject()
        name("id").value(value.id)
        name("todoId").value(value.todoId)
        name("relation").value(value.relation)
        name("amount").value(value.amount.toLong())
        name("unit")
        if (value.relation == "at") nullValue() else value(value.unit)
        name("sortOrder").value(value.sortOrder.toLong())
        name("createdAt").value(value.createdAt)
        name("updatedAt").value(value.updatedAt)
        endObject()
    }

    private fun JsonWriter.writeExecution(value: TodoExecutionEntity) {
        beginObject()
        name("id").value(value.id)
        name("operationId").value(value.operationId)
        name("todoId").value(value.todoId)
        name("logicalDate").value(value.logicalDate)
        name("status").value(value.status)
        name("actedAt").nullable(value.actedAt)
        name("finalizedAt").value(value.finalizedAt)
        name("definitionRevision").value(value.definitionRevision.toLong())
        name("snapshotVersion").value(value.snapshotVersion.toLong())
        name("snapshot").jsonObject(value.snapshotJson)
        endObject()
    }

    private fun JsonWriter.writePeriodResult(value: PeriodResultEntity) {
        beginObject()
        name("id").value(value.id)
        name("todoId").value(value.todoId)
        name("periodType").value(value.periodType)
        name("periodStart").value(value.periodStart)
        name("periodEnd").value(value.periodEnd)
        name("requiredCount").value(value.requiredCount.toLong())
        name("completedCount").value(value.completedCount.toLong())
        name("achieved").value(value.achieved)
        name("displayDate").value(value.displayDate)
        name("finalizedAt").value(value.finalizedAt)
        name("definitionRevision").value(value.definitionRevision.toLong())
        name("snapshotVersion").value(value.snapshotVersion.toLong())
        name("snapshot").jsonObject(value.snapshotJson)
        endObject()
    }

    private fun JsonWriter.writeRuntimeState(value: TodoRuntimeStateEntity) {
        beginObject()
        name("todoId").value(value.todoId)
        name("lastFinalizedLogicalDate").nullable(value.lastFinalizedLogicalDate)
        name("lastFinalizedWeeklyPeriodEnd").nullable(value.lastFinalizedWeeklyPeriodEnd)
        name("lastFinalizedMonthlyPeriodEnd").nullable(value.lastFinalizedMonthlyPeriodEnd)
        name("appliedDefinitionRevision").value(value.appliedDefinitionRevision.toLong())
        name("reconciliationCursorDate").nullable(value.reconciliationCursorDate)
        name("updatedAt").value(value.updatedAt)
        endObject()
    }

    private fun writeManifest(output: OutputStream, manifest: BackupManifest) {
        JsonWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            writer.beginObject()
            writer.name("formatId").value(BACKUP_FORMAT_ID)
            writer.name("formatVersion").value(BACKUP_FORMAT_VERSION.toLong())
            writer.name("minimumReaderVersion").value(BACKUP_FORMAT_VERSION.toLong())
            writer.name("backupId").value(manifest.backupId)
            writer.name("createdAt").value(manifest.createdAt)
            writer.name("appVersionName").value(manifest.appVersionName)
            writer.name("appVersionCode").value(manifest.appVersionCode)
            writer.name("roomSchemaVersion").value(manifest.roomSchemaVersion.toLong())
            writer.name("data").beginObject()
            writer.name("sha256").value(manifest.dataSha256)
            writer.name("uncompressedBytes").value(manifest.dataUncompressedBytes)
            writer.endObject()
            writer.name("counts").beginObject()
            writer.name("categories").value(manifest.counts.categories.toLong())
            writer.name("todos").value(manifest.counts.todos.toLong())
            writer.name("notifications").value(manifest.counts.notifications.toLong())
            writer.name("executions").value(manifest.counts.executions.toLong())
            writer.name("periodResults").value(manifest.counts.periodResults.toLong())
            writer.name("runtimeStates").value(manifest.counts.runtimeStates.toLong())
            writer.endObject()
            writer.endObject()
        }
    }

    private fun JsonWriter.jsonObject(raw: String) {
        val element = Json.parseToJsonElement(raw)
        require(element is JsonObject)
        writeElement(element)
    }

    private fun JsonWriter.writeElement(element: JsonElement) {
        when (element) {
            JsonNull -> nullValue()
            is JsonObject -> {
                beginObject()
                element.forEach { (name, value) ->
                    name(name)
                    writeElement(value)
                }
                endObject()
            }
            is JsonArray -> {
                beginArray()
                element.forEach { value -> writeElement(value) }
                endArray()
            }
            is JsonPrimitive -> when {
                element.isString -> value(element.content)
                element.booleanOrNull != null -> value(element.booleanOrNull!!)
                element.longOrNull != null -> value(element.longOrNull!!)
                else -> throw IllegalArgumentException("Backup JSON contains a non-integer number")
            }
        }
    }

    private fun JsonWriter.nullable(value: String?) {
        if (value == null) nullValue() else value(value)
    }

    private fun JsonWriter.nullable(value: Int?) {
        if (value == null) nullValue() else value(value.toLong())
    }

    private fun JsonWriter.nullable(value: Long?) {
        if (value == null) nullValue() else value(value)
    }

    private fun DayOfWeek.backupCode(): String = name.lowercase()

    @Suppress("DEPRECATION")
    private fun appVersion(): Pair<String, Long> {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return info.versionName.orEmpty() to code
    }

    private companion object {
        const val DATA_ENTRY = "data.json"
        const val MANIFEST_ENTRY = "manifest.json"
        const val PAGE_SIZE = 256
    }
}

private class NonClosingOutputStream(private val delegate: OutputStream) : OutputStream() {
    override fun write(value: Int) = delegate.write(value)
    override fun write(buffer: ByteArray, offset: Int, length: Int) = delegate.write(buffer, offset, length)
    override fun flush() = delegate.flush()
    override fun close() = flush()
}

private class DigestCountingOutputStream(private val delegate: OutputStream) : OutputStream() {
    private val digest = MessageDigest.getInstance("SHA-256")
    var byteCount: Long = 0
        private set

    override fun write(value: Int) {
        delegate.write(value)
        digest.update(value.toByte())
        byteCount += 1
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        delegate.write(buffer, offset, length)
        digest.update(buffer, offset, length)
        byteCount += length
    }

    override fun flush() = delegate.flush()

    fun sha256(): String = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
