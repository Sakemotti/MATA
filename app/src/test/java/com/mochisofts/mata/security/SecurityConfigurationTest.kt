package com.mochisofts.mata.security

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class SecurityConfigurationTest {
    @Test
    fun manifestDisablesAutomaticBackupAndCleartextTraffic() {
        val application = document("src/main/AndroidManifest.xml")
            .elements("application")
            .single()

        assertEquals("false", application.androidAttribute("allowBackup"))
        assertEquals("false", application.androidAttribute("fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.androidAttribute("dataExtractionRules"))
        assertEquals("false", application.androidAttribute("usesCleartextTraffic"))
        assertEquals(
            "@xml/network_security_config",
            application.androidAttribute("networkSecurityConfig"),
        )
    }

    @Test
    fun manifestRequestsOnlyApprovedApplicationPermissions() {
        val permissions = document("src/main/AndroidManifest.xml")
            .elements("uses-permission")
            .map { it.androidAttribute("name") }
            .toSet()

        assertEquals(
            setOf(
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.SCHEDULE_EXACT_ALARM",
            ),
            permissions,
        )
        assertFalse("android.permission.USE_EXACT_ALARM" in permissions)
    }

    @Test
    fun manifestExportsOnlyLauncherAndWidgetEntryPoints() {
        val manifest = document("src/main/AndroidManifest.xml")
        val components = COMPONENT_TAGS.flatMap { manifest.elements(it) }
        val exported = components
            .filter { it.androidAttribute("exported") == "true" }
            .map { it.androidAttribute("name") }
            .toSet()

        assertEquals(
            setOf(
                ".app.MainActivity",
                ".widget.TodayTodoWidgetReceiver",
            ),
            exported,
        )
        components
            .filterNot { it.androidAttribute("name") in exported }
            .forEach { component ->
                assertEquals(
                    "Internal component ${component.androidAttribute("name")} must be explicit",
                    "false",
                    component.androidAttribute("exported"),
                )
            }
    }

    @Test
    fun networkSecurityRejectsCleartextAndTrustsOnlySystemCertificates() {
        val config = document("src/main/res/xml/network_security_config.xml")
        val baseConfig = config.elements("base-config").single()
        val certificateSources = config.elements("certificates")
            .map { it.attribute("src") }
            .toSet()

        assertEquals("false", baseConfig.attribute("cleartextTrafficPermitted"))
        assertEquals(setOf("system"), certificateSources)
        assertTrue(config.elements("debug-overrides").isEmpty())
        assertTrue(
            config.elements("domain-config")
                .none { it.attribute("cleartextTrafficPermitted") == "true" },
        )
    }

    @Test
    fun extractionRulesExcludeEveryApplicationDataDomain() {
        val rules = document("src/main/res/xml/data_extraction_rules.xml")
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

        listOf("cloud-backup", "device-transfer").forEach { sectionName ->
            val section = rules.elements(sectionName).single()
            val exclusions = section.childElements("exclude")
            assertEquals(expectedDomains, exclusions.map { it.attribute("domain") }.toSet())
            assertTrue(exclusions.all { it.attribute("path") == "." })
        }
    }

    private fun document(relativePath: String): Document {
        val file = locate(relativePath)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        return factory.newDocumentBuilder().parse(file).also { it.documentElement.normalize() }
    }

    private fun locate(relativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        )
        val located = candidates.firstOrNull(File::isFile)
        assertNotNull(
            "Could not locate $relativePath from $workingDirectory",
            located,
        )
        return requireNotNull(located)
    }

    private fun Document.elements(tagName: String): List<Element> =
        documentElement.getElementsByTagName(tagName).asElements()

    private fun Element.childElements(tagName: String): List<Element> =
        getElementsByTagName(tagName).asElements()

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.attribute(name: String): String = getAttribute(name)

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private val COMPONENT_TAGS = listOf("activity", "activity-alias", "provider", "receiver", "service")
    }
}
