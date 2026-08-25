package com.mochisofts.mata.core.observability

import android.os.SystemClock
import android.util.Log
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.core.common.FailureCategory
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

enum class DiagnosticResult(val value: String) {
    SUCCESS("success"),
    RETRY("retry"),
    CANCELLED("cancelled"),
    FAILURE("failure"),
}

/**
 * Fixed event codes are the only descriptions accepted by [DiagnosticLogger].
 * This deliberately prevents user-entered text and SDK response bodies from reaching Logcat.
 */
enum class DiagnosticEventCode(val component: String) {
    STARTUP_REQUIRED_START("Startup"),
    STARTUP_REQUIRED_FINISH("Startup"),
    STARTUP_REQUIRED_RETRY("Startup"),
    WIDGET_UPDATE_ENQUEUED("Widget"),
    WIDGET_PERIODIC_SCHEDULED("Widget"),
    WIDGET_PERIODIC_CANCELLED("Widget"),
    WIDGET_REFRESH_FINISHED("Widget"),
    WIDGET_ALARM_SCHEDULED("Widget"),
    WIDGET_ALARM_CANCELLED("Widget"),
}

data class DiagnosticEvent(
    val code: DiagnosticEventCode,
    val level: DiagnosticLevel,
    val result: DiagnosticResult? = null,
    val failureCategory: FailureCategory? = null,
    val count: Int? = null,
    val durationMillis: Long? = null,
    val operationId: String? = null,
    val attempt: Int? = null,
) {
    init {
        require(count == null || count >= 0)
        require(durationMillis == null || durationMillis >= 0)
        require(attempt == null || attempt >= 0)
        require(operationId == null || OPERATION_ID_PATTERN.matches(operationId))
    }

    private companion object {
        val OPERATION_ID_PATTERN = Regex("[0-9a-f]{12}")
    }
}

@Singleton
class DiagnosticLogger @Inject constructor() {
    private val delegate = StructuredDiagnosticLogger(
        isDebugBuild = BuildConfig.DEBUG,
        sink = LogcatDiagnosticSink,
        elapsedRealtimeMillis = SystemClock::elapsedRealtime,
    )

    fun record(event: DiagnosticEvent) = delegate.record(event)

    fun newOperationId(): String = UUID.randomUUID()
        .toString()
        .replace("-", "")
        .take(OPERATION_ID_LENGTH)

    private companion object {
        const val OPERATION_ID_LENGTH = 12
    }
}

internal fun interface DiagnosticSink {
    fun write(level: DiagnosticLevel, tag: String, payload: String)
}

internal class StructuredDiagnosticLogger(
    private val isDebugBuild: Boolean,
    private val sink: DiagnosticSink,
    private val elapsedRealtimeMillis: () -> Long,
    private val retryDeduplicationMillis: Long = DEFAULT_RETRY_DEDUPLICATION_MILLIS,
) {
    private val retryLogTimes = mutableMapOf<RetryKey, Long>()

    @Synchronized
    fun record(event: DiagnosticEvent) {
        if (!isDebugBuild && event.level in DEBUG_ONLY_LEVELS) return
        val now = elapsedRealtimeMillis()
        if (event.result == DiagnosticResult.RETRY) {
            val key = RetryKey(event.code, event.failureCategory)
            val previous = retryLogTimes[key]
            if (previous != null && now - previous < retryDeduplicationMillis) return
            retryLogTimes[key] = now
        } else {
            retryLogTimes.keys.removeAll { it.code == event.code }
        }
        sink.write(event.level, "MATA/${event.code.component}", event.format())
    }

    private fun DiagnosticEvent.format(): String = buildString {
        append(code.name)
        result?.let { append(" result=${it.value}") }
        failureCategory?.let { append(" failure=${it.name.lowercase()}") }
        count?.let { append(" count=$it") }
        durationMillis?.let { append(" duration_ms=$it") }
        operationId?.let { append(" operation_id=$it") }
        attempt?.let { append(" attempt=$it") }
    }

    private data class RetryKey(
        val code: DiagnosticEventCode,
        val category: FailureCategory?,
    )

    private companion object {
        const val DEFAULT_RETRY_DEDUPLICATION_MILLIS = 60_000L
        val DEBUG_ONLY_LEVELS = setOf(DiagnosticLevel.VERBOSE, DiagnosticLevel.DEBUG)
    }
}

private object LogcatDiagnosticSink : DiagnosticSink {
    override fun write(level: DiagnosticLevel, tag: String, payload: String) {
        when (level) {
            DiagnosticLevel.VERBOSE -> Log.v(tag, payload)
            DiagnosticLevel.DEBUG -> Log.d(tag, payload)
            DiagnosticLevel.INFO -> Log.i(tag, payload)
            DiagnosticLevel.WARN -> Log.w(tag, payload)
            DiagnosticLevel.ERROR -> Log.e(tag, payload)
        }
    }
}
