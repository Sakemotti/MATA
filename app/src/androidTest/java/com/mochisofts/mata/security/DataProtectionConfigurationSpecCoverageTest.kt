package com.mochisofts.mata.security

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class DataProtectionConfigurationSpecCoverageTest {
    @Test
    fun dat005_installedApplicationRejectsEveryOsAutomaticBackupPath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)

        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)

        val exclusionsBySection = mutableMapOf<String, MutableMap<String, String>>()
        context.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            var currentSection: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "cloud-backup", "device-transfer" -> currentSection = parser.name
                        "exclude" -> currentSection?.let { section ->
                            exclusionsBySection.getOrPut(section, ::mutableMapOf)[
                                requireNotNull(parser.getAttributeValue(null, "domain"))
                            ] = requireNotNull(parser.getAttributeValue(null, "path"))
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == currentSection) currentSection = null
                }
                parser.next()
            }
        }

        val expectedDomains = setOf(
            "root",
            "file",
            "database",
            "sharedpref",
            "external",
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        )
        assertEquals(setOf("cloud-backup", "device-transfer"), exclusionsBySection.keys)
        exclusionsBySection.values.forEach { exclusions ->
            assertEquals(expectedDomains, exclusions.keys)
            assertTrue(exclusions.values.all { it == "." })
        }
    }
}
