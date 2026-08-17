package com.mochisofts.mata.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupArchiveReaderTest {
    @Test
    fun validEmptyBackup_isAccepted() = runTest {
        val data = EMPTY_DATA.toByteArray(Charsets.UTF_8)
        val backup = archive(data, sha256(data))
        val output = temporaryDataFile()

        val result = BackupArchiveReader().extractAndValidate(ByteArrayInputStream(backup), output)

        assertEquals(0, result.manifest.counts.todos)
        assertEquals(0, result.archivedTodoCount)
        output.delete()
    }

    @Test
    fun mismatchedDigest_isRejectedBeforeRestore() = runTest {
        val data = EMPTY_DATA.toByteArray(Charsets.UTF_8)
        val backup = archive(data, "0".repeat(64))
        val output = temporaryDataFile()

        val error = runCatching {
            BackupArchiveReader().extractAndValidate(ByteArrayInputStream(backup), output)
        }.exceptionOrNull()
        assertTrue(error is BackupFormatException)
        output.delete()
    }

    private fun archive(data: ByteArray, sha: String): ByteArray {
        val manifest = """
            {"formatId":"com.mochisofts.mata.backup","formatVersion":1,"minimumReaderVersion":1,"backupId":"00000000-0000-0000-0000-000000000001","createdAt":1,"appVersionName":"1.0","appVersionCode":1,"roomSchemaVersion":7,"data":{"sha256":"$sha","uncompressedBytes":${data.size}},"counts":{"categories":0,"todos":0,"notifications":0,"executions":0,"periodResults":0,"runtimeStates":0}}
        """.trimIndent().toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("data.json").apply { time = ENTRY_TIME })
                zip.write(data)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json").apply { time = ENTRY_TIME })
                zip.write(manifest)
                zip.closeEntry()
            }
        }.toByteArray()
    }

    private fun temporaryDataFile() = java.io.File.createTempFile(
        "backup-reader-",
        ".json",
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
    )

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ENTRY_TIME = 1_700_000_000_000L
        const val EMPTY_DATA = """{"formatVersion":1,"settings":{"uncategorizedEndHour":0,"weekStartDay":"monday","showCompletedTodos":false,"theme":"system"},"categories":[],"todos":[],"notifications":[],"executions":[],"periodResults":[],"runtimeStates":[]}"""
    }
}
