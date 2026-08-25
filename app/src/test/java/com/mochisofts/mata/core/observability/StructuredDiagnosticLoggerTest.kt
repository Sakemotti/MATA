package com.mochisofts.mata.core.observability

import com.mochisofts.mata.core.common.FailureCategory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredDiagnosticLoggerTest {
    @Test
    fun eventUsesFixedTagAndOrderedSafeMetadata() {
        val sink = RecordingSink()
        val logger = logger(sink = sink)

        logger.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_REFRESH_FINISHED,
                level = DiagnosticLevel.WARN,
                result = DiagnosticResult.FAILURE,
                failureCategory = FailureCategory.TEMPORARY_LOCAL,
                count = 2,
                durationMillis = 135,
                operationId = "012345abcdef",
                attempt = 1,
            ),
        )

        assertEquals(
            CapturedLog(
                level = DiagnosticLevel.WARN,
                tag = "MATA/Widget",
                payload = "WIDGET_REFRESH_FINISHED result=failure " +
                    "failure=temporary_local count=2 duration_ms=135 " +
                    "operation_id=012345abcdef attempt=1",
            ),
            sink.logs.single(),
        )
    }

    @Test
    fun releaseSuppressesDebugButKeepsImportantEvents() {
        val sink = RecordingSink()
        val logger = logger(isDebugBuild = false, sink = sink)

        logger.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_ALARM_SCHEDULED,
                level = DiagnosticLevel.VERBOSE,
            ),
        )
        logger.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.WIDGET_UPDATE_ENQUEUED,
                level = DiagnosticLevel.DEBUG,
            ),
        )
        logger.record(
            DiagnosticEvent(
                code = DiagnosticEventCode.STARTUP_REQUIRED_FINISH,
                level = DiagnosticLevel.INFO,
                result = DiagnosticResult.SUCCESS,
            ),
        )

        assertEquals(1, sink.logs.size)
        assertTrue(sink.logs.single().payload.startsWith("STARTUP_REQUIRED_FINISH"))
    }

    @Test
    fun repeatedRetryIsThrottledUntilWindowExpiresOrStateChanges() {
        var now = 1_000L
        val sink = RecordingSink()
        val logger = logger(sink = sink, elapsedRealtimeMillis = { now })
        val retry = DiagnosticEvent(
            code = DiagnosticEventCode.STARTUP_REQUIRED_RETRY,
            level = DiagnosticLevel.WARN,
            result = DiagnosticResult.RETRY,
            failureCategory = FailureCategory.TEMPORARY_LOCAL,
        )

        logger.record(retry)
        now += 10_000
        logger.record(retry)
        logger.record(retry.copy(failureCategory = FailureCategory.NETWORK))
        logger.record(retry.copy(result = DiagnosticResult.SUCCESS))
        logger.record(retry)

        assertEquals(4, sink.logs.size)

        now += 60_000
        logger.record(retry)
        assertEquals(5, sink.logs.size)
    }

    @Test
    fun diagnosticMetadataRejectsPersistentOrFreeFormOperationIds() {
        val failure = runCatching {
            DiagnosticEvent(
                code = DiagnosticEventCode.STARTUP_REQUIRED_START,
                level = DiagnosticLevel.INFO,
                operationId = "todo-title-or-persistent-uuid",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun directLogcatCallsAreCentralizedAndMonitoringSdksAreAbsent() {
        val sourceRoot = locate("src/main/java")
        val directLogging = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Diagnostics.kt" }
            .filter { file ->
                val source = file.readText()
                "android.util.Log" in source || DIRECT_LOG_CALL.containsMatchIn(source)
            }
            .toList()
        assertTrue("Direct Logcat calls found: $directLogging", directLogging.isEmpty())

        val buildFile = locate("build.gradle").readText()
        assertFalse("firebase-crashlytics" in buildFile)
        assertFalse("firebase-analytics" in buildFile)
    }

    private fun logger(
        isDebugBuild: Boolean = true,
        sink: RecordingSink,
        elapsedRealtimeMillis: () -> Long = { 0L },
    ) = StructuredDiagnosticLogger(
        isDebugBuild = isDebugBuild,
        sink = sink,
        elapsedRealtimeMillis = elapsedRealtimeMillis,
    )

    private fun locate(relativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        ).firstOrNull(File::exists) ?: error("Could not locate $relativePath from $workingDirectory")
    }

    private class RecordingSink : DiagnosticSink {
        val logs = mutableListOf<CapturedLog>()

        override fun write(level: DiagnosticLevel, tag: String, payload: String) {
            logs += CapturedLog(level, tag, payload)
        }
    }

    private data class CapturedLog(
        val level: DiagnosticLevel,
        val tag: String,
        val payload: String,
    )

    private companion object {
        val DIRECT_LOG_CALL = Regex("\\bLog\\.[vdiew]\\(")
    }
}
